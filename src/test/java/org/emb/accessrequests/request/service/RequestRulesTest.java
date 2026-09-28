package org.emb.accessrequests.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.emb.accessrequests.accesstype.entity.AccessType;
import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.request.entity.AccessRequest;
import org.emb.accessrequests.request.entity.Approval;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.request.enums.RequestStatus;
import org.emb.accessrequests.user.entity.User;
import org.emb.accessrequests.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests (no Spring, no database) for the state machine and the visibility rules. */
class RequestRulesTest {

    private static final long MARIA_ID = 2;
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-01T01:00:00Z");

    private final User alice = new User("alice", "Alice Anderson", "hash", Role.USER, MARIA_ID);
    private final User reviewer = new User("reviewer", "Some Reviewer", "hash", Role.MANAGER, null);
    private final AccessType vpn = new AccessType("VPN Access", "VPN");

    private final SessionUser maria = new SessionUser(MARIA_ID, "maria", "Maria Manager", Role.MANAGER, null);
    private final SessionUser mark = new SessionUser(3, "mark", "Mark Manager", Role.MANAGER, null);
    private final SessionUser admin = new SessionUser(1, "admin", "Adam Admin", Role.ADMIN, null);
    private final SessionUser aliceSession = new SessionUser(4, "alice", "Alice Anderson", Role.USER, MARIA_ID);

    private AccessRequest newRequest() {
        return new AccessRequest(alice, vpn, "Need it", T0);
    }

    @Test
    void aNewRequestWaitsForTheManager() {
        AccessRequest request = newRequest();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(request.getCurrentStep()).isEqualTo(ApprovalStep.MANAGER);
        assertThat(request.getApprovals()).isEmpty();
    }

    @ParameterizedTest(name = "{0} + {1} -> {2} / {3}")
    @CsvSource(nullValues = "null", value = {
            "MANAGER, APPROVED, IN_PROGRESS, ADMIN",
            "MANAGER, REJECTED, REJECTED,    null",
            "ADMIN,   APPROVED, APPROVED,    null",
            "ADMIN,   REJECTED, REJECTED,    null",
    })
    void transitions(ApprovalStep from, Decision decision, RequestStatus status, ApprovalStep step) {
        AccessRequest request = newRequest();
        if (from == ApprovalStep.ADMIN) {
            request.decide(reviewer, Decision.APPROVED, null, T0);
        }

        Approval approval = request.decide(reviewer, decision, "why", T1);

        assertThat(approval.getStep()).isEqualTo(from);
        assertThat(approval.getDecision()).isEqualTo(decision);
        assertThat(approval.getDecidedAt()).isEqualTo(T1);
        assertThat(request.getStatus()).isEqualTo(status);
        assertThat(request.getCurrentStep()).isEqualTo(step);
        assertThat(request.getUpdatedAt()).isEqualTo(T1);
        assertThat(request.getApprovals()).last().isSameAs(approval);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "REJECTED"})
    void aFinalRequestCannotBeDecidedAgain(Decision finalDecision) {
        AccessRequest request = newRequest();
        request.decide(reviewer, Decision.APPROVED, null, T0);
        request.decide(reviewer, finalDecision, "x", T0);

        assertThatThrownBy(() -> request.decide(reviewer, Decision.APPROVED, null, T1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(request.getApprovals()).hasSize(2);
    }

    @Test
    void theRequestersManagerSeesItInEveryStatus_otherManagersNever() {
        AccessRequest waiting = newRequest();
        AccessRequest rejected = newRequest();
        rejected.decide(reviewer, Decision.REJECTED, "no", T1);

        for (AccessRequest request : new AccessRequest[] {waiting, rejected}) {
            assertThat(RequestRules.isVisibleToReviewer(maria, request)).isTrue();
            assertThat(RequestRules.isVisibleToReviewer(mark, request)).isFalse();
        }
    }

    @Test
    void adminSeesOnlyRequestsAManagerApproved() {
        AccessRequest waitingForManager = newRequest();
        AccessRequest rejectedByManager = newRequest();
        rejectedByManager.decide(reviewer, Decision.REJECTED, "no", T1);
        AccessRequest waitingForAdmin = newRequest();
        waitingForAdmin.decide(reviewer, Decision.APPROVED, null, T1);
        AccessRequest approved = newRequest();
        approved.decide(reviewer, Decision.APPROVED, null, T1);
        approved.decide(reviewer, Decision.APPROVED, null, T1);
        AccessRequest rejectedByAdmin = newRequest();
        rejectedByAdmin.decide(reviewer, Decision.APPROVED, null, T1);
        rejectedByAdmin.decide(reviewer, Decision.REJECTED, "no", T1);

        assertThat(RequestRules.isVisibleToReviewer(admin, waitingForManager)).isFalse();
        assertThat(RequestRules.isVisibleToReviewer(admin, rejectedByManager)).isFalse();
        assertThat(RequestRules.isVisibleToReviewer(admin, waitingForAdmin)).isTrue();
        assertThat(RequestRules.isVisibleToReviewer(admin, approved)).isTrue();
        assertThat(RequestRules.isVisibleToReviewer(admin, rejectedByAdmin)).isTrue();
    }

    @Test
    void usersNeverSeeRequestsAsReviewers() {
        assertThat(RequestRules.isVisibleToReviewer(aliceSession, newRequest())).isFalse();
    }

    @Test
    void canActOnlyAtTheReviewersOwnStep() {
        AccessRequest atManager = newRequest();
        AccessRequest atAdmin = newRequest();
        atAdmin.decide(reviewer, Decision.APPROVED, null, T1);
        AccessRequest done = newRequest();
        done.decide(reviewer, Decision.REJECTED, "no", T1);

        assertThat(RequestRules.canAct(maria, atManager)).isTrue();
        assertThat(RequestRules.canAct(maria, atAdmin)).isFalse();
        assertThat(RequestRules.canAct(admin, atAdmin)).isTrue();
        assertThat(RequestRules.canAct(admin, atManager)).isFalse();
        assertThat(RequestRules.canAct(maria, done)).isFalse();
        assertThat(RequestRules.canAct(admin, done)).isFalse();
        assertThat(RequestRules.canAct(aliceSession, atManager)).isFalse();
    }
}
