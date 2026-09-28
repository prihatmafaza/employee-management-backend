package org.emb.accessrequests.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.emb.accessrequests.IntegrationTest;
import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.error.ApiException;
import org.emb.accessrequests.request.dto.RequestDtos.AccessRequestDto;
import org.emb.accessrequests.request.dto.RequestDtos.ReviewItemDto;
import org.emb.accessrequests.request.dto.RequestDtos;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.request.enums.RequestStatus;
import org.emb.accessrequests.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RequestService against the real database: check order, messages, transitions and visibility. */
class RequestServiceTest extends IntegrationTest {

    private static final SessionUser ADMIN = new SessionUser(1, "admin", "Adam Admin", Role.ADMIN, null);
    private static final SessionUser MARIA = new SessionUser(2, "maria", "Maria Manager", Role.MANAGER, null);
    private static final SessionUser MARK = new SessionUser(3, "mark", "Mark Manager", Role.MANAGER, null);
    private static final SessionUser ALICE = new SessionUser(4, "alice", "Alice Anderson", Role.USER, 2L);
    private static final SessionUser BOB = new SessionUser(5, "bob", "Bob Brown", Role.USER, 2L);
    private static final SessionUser ALICE_WITHOUT_MANAGER =
            new SessionUser(4, "alice", "Alice Anderson", Role.USER, null);

    @Autowired
    RequestService service;

    // --- submit: checks in contract order ---

    @Test
    void submitChecksRunInContractOrder() {
        service.submit(ALICE, VPN, "pending one");

        expectError(() -> service.submit(ALICE_WITHOUT_MANAGER, 999L, "  "),
                400, "VALIDATION_ERROR", "Please enter a reason for the request.");
        expectError(() -> service.submit(ALICE_WITHOUT_MANAGER, 999L, null),
                400, "VALIDATION_ERROR", "Please enter a reason for the request.");
        expectError(() -> service.submit(ALICE_WITHOUT_MANAGER, 999L, "x".repeat(501)),
                400, "VALIDATION_ERROR", "Reason must be at most 500 characters.");
        expectError(() -> service.submit(ALICE_WITHOUT_MANAGER, 999L, "ok"),
                400, "VALIDATION_ERROR", "Please select a valid access.");
        expectError(() -> service.submit(ALICE_WITHOUT_MANAGER, null, "ok"),
                400, "VALIDATION_ERROR", "Please select a valid access.");
        expectError(() -> service.submit(ALICE_WITHOUT_MANAGER, VPN, "ok"),
                400, "VALIDATION_ERROR", "You have no manager assigned, so your request cannot be approved.");
        expectError(() -> service.submit(ALICE, VPN, "ok"),
                409, "DUPLICATE_REQUEST", "You already have a pending request for VPN Access.");
    }

    @Test
    void reasonLengthIsCheckedAfterTrimming() {
        AccessRequestDto created = service.submit(ALICE, VPN, "  " + "x".repeat(500) + "  ");

        assertThat(created.reason()).hasSize(500);
    }

    @Test
    void anotherUsersRequestIsNotADuplicate() {
        service.submit(ALICE, VPN, "mine");

        assertThat(service.submit(BOB, VPN, "mine too").status()).isEqualTo(RequestStatus.IN_PROGRESS);
    }

    // --- decide: checks in contract order ---

    @Test
    void invalidInputComesBeforeNotFound() {
        expectError(() -> service.decide(MARIA, 999, "MAYBE", null), 400, "VALIDATION_ERROR",
                "Decision must be APPROVED or REJECTED.");
        expectError(() -> service.decide(MARIA, 999, null, null), 400, "VALIDATION_ERROR",
                "Decision must be APPROVED or REJECTED.");
        expectError(() -> service.decide(MARIA, 999, "APPROVED", "x".repeat(501)), 400, "VALIDATION_ERROR",
                "Comment must be at most 500 characters.");
        expectError(() -> service.decide(MARIA, 999, "APPROVED", null), 404, "NOT_FOUND", "Request not found.");
    }

    @Test
    void notFoundComesBeforeFinalized() {
        long id = service.submit(ALICE, VPN, "x").id();
        service.decide(MARIA, id, "REJECTED", "no");

        expectError(() -> service.decide(MARK, id, "APPROVED", null), 404, "NOT_FOUND", "Request not found.");
        expectError(() -> service.decide(ADMIN, id, "APPROVED", null), 404, "NOT_FOUND", "Request not found.");
    }

    @Test
    void finalizedComesBeforeMissingRejectionComment() {
        long id = service.submit(ALICE, VPN, "x").id();
        service.decide(MARIA, id, "APPROVED", null);
        service.decide(ADMIN, id, "APPROVED", null);

        expectError(() -> service.decide(ADMIN, id, "REJECTED", ""), 409, "ALREADY_FINALIZED",
                "This request has already been finalized.");
    }

    @Test
    void wrongStepComesBeforeMissingRejectionComment() {
        long id = service.submit(ALICE, VPN, "x").id();
        service.decide(MARIA, id, "APPROVED", null);

        expectError(() -> service.decide(MARIA, id, "REJECTED", ""), 409, "NOT_YOUR_STEP",
                "This request is waiting for admin approval.");
    }

    @Test
    void anEmptyApprovalCommentIsStoredAsNull() {
        long id = service.submit(ALICE, VPN, "x").id();

        AccessRequestDto decided = service.decide(MARIA, id, "APPROVED", "   ");

        assertThat(decided.approvals()).singleElement().satisfies(a -> assertThat(a.comment()).isNull());
        assertThat(jdbc.queryForObject("SELECT comment FROM approvals WHERE request_id = ?", String.class, id))
                .isNull();
    }

    @Test
    void usersCannotDecide_andReviewersCannotSubmit() {
        expectError(() -> service.decide(ALICE, 1, "APPROVED", null), 403, "FORBIDDEN", null);
        expectError(() -> service.submit(MARIA, VPN, "x"), 403, "FORBIDDEN", null);
        expectError(() -> service.listReviewItems(ALICE), 403, "FORBIDDEN", null);
    }

    // --- transitions, persisted ---

    @Test
    void transitionsArePersisted() {
        long managerRejects = service.submit(ALICE, VPN, "a").id();
        long adminApproves = service.submit(ALICE, GITHUB, "b").id();
        long adminRejects = service.submit(ALICE, FIGMA, "c").id();

        assertState(service.decide(MARIA, managerRejects, "REJECTED", "no"), RequestStatus.REJECTED, null);
        assertState(service.decide(MARIA, adminApproves, "APPROVED", null), RequestStatus.IN_PROGRESS,
                ApprovalStep.ADMIN);
        assertState(service.decide(ADMIN, adminApproves, "APPROVED", null), RequestStatus.APPROVED, null);
        service.decide(MARIA, adminRejects, "APPROVED", null);
        assertState(service.decide(ADMIN, adminRejects, "REJECTED", "no"), RequestStatus.REJECTED, null);

        List<AccessRequestDto> stored = service.listMine(ALICE);
        assertThat(stored).extracting(AccessRequestDto::id).containsExactly(adminRejects, adminApproves, managerRejects);
        assertThat(stored).extracting(AccessRequestDto::status).containsExactly(
                RequestStatus.REJECTED, RequestStatus.APPROVED, RequestStatus.REJECTED);
        assertThat(stored.get(1).approvals()).extracting(RequestDtos.ApprovalDto::step)
                .containsExactly(ApprovalStep.MANAGER, ApprovalStep.ADMIN);
    }

    // --- visibility of review items ---

    @Test
    void adminSeesOnlyManagerApprovedRequests() {
        long waitingForManager = service.submit(ALICE, VPN, "a").id();
        long rejectedByManager = service.submit(ALICE, GITHUB, "b").id();
        long waitingForAdmin = service.submit(ALICE, FIGMA, "c").id();
        long approved = service.submit(BOB, VPN, "d").id();
        long rejectedByAdmin = service.submit(BOB, GITHUB, "e").id();
        service.decide(MARIA, rejectedByManager, "REJECTED", "no");
        service.decide(MARIA, waitingForAdmin, "APPROVED", null);
        service.decide(MARIA, approved, "APPROVED", null);
        service.decide(MARIA, rejectedByAdmin, "APPROVED", null);
        service.decide(ADMIN, approved, "APPROVED", null);
        service.decide(ADMIN, rejectedByAdmin, "REJECTED", "no");

        List<ReviewItemDto> adminItems = service.listReviewItems(ADMIN);
        assertThat(adminItems).extracting(item -> item.request().id())
                .containsExactly(rejectedByAdmin, approved, waitingForAdmin)
                .doesNotContain(waitingForManager, rejectedByManager);
        assertThat(adminItems).extracting(ReviewItemDto::canAct).containsExactly(false, false, true);

        assertThat(service.listReviewItems(MARIA)).hasSize(5);
        assertThat(service.listReviewItems(MARK)).isEmpty();
    }

    private static void assertState(AccessRequestDto dto, RequestStatus status, ApprovalStep step) {
        assertThat(dto.status()).isEqualTo(status);
        assertThat(dto.currentStep()).isEqualTo(step);
    }

    private static void expectError(ThrowingCallable call, int status, String code, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status().value()).isEqualTo(status);
            assertThat(e.code()).isEqualTo(code);
            if (message != null) {
                assertThat(e.getMessage()).isEqualTo(message);
            }
        });
    }
}
