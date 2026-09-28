package org.emb.accessrequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.emb.accessrequests.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The acceptance scenarios from docs/api-contract.md, numbered as there. */
class AcceptanceScenariosTest extends IntegrationTest {

    @Test
    void s01_everyEndpointExceptLoginNeedsASession_andWrongPasswordIs401() {
        ApiClient anonymous = anonymous();
        List<Response> responses = List.of(
                anonymous.get("/api/auth/me"),
                anonymous.get("/api/access-types"),
                anonymous.get("/api/requests/mine"),
                anonymous.submit(VPN, "Need it"),
                anonymous.reviewItems(),
                anonymous.decide(1, "APPROVED", null),
                anonymous.get("/api/dashboard/summary"));
        for (Response response : responses) {
            assertThat(response.status()).isEqualTo(401);
            assertThat(response.errorCode()).isEqualTo("UNAUTHENTICATED");
        }

        Response wrongPassword = anonymous.post("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"wrong\"}");
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.errorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(wrongPassword.errorMessage()).isEqualTo("Invalid username or password.");

        Response unknownUser = anonymous.post("/api/auth/login",
                "{\"username\":\"nobody\",\"password\":\"password123\"}");
        assertThat(unknownUser.status()).isEqualTo(401);
        assertThat(unknownUser.errorMessage()).isEqualTo(wrongPassword.errorMessage());
    }

    @Test
    void s02_meReturnsTheUserWithoutAnyPasswordField() {
        Response me = as("alice").get("/api/auth/me");

        assertThat(me.status()).isEqualTo(200);
        assertThat(fieldNames(me.body())).containsExactlyInAnyOrder("id", "username", "fullName", "role", "managerId");
        assertThat(me.body().path("id").asLong()).isEqualTo(4);
        assertThat(me.body().path("username").asString()).isEqualTo("alice");
        assertThat(me.body().path("fullName").asString()).isEqualTo("Alice Anderson");
        assertThat(me.body().path("role").asString()).isEqualTo("USER");
        assertThat(me.body().path("managerId").asLong()).isEqualTo(2);
    }

    @Test
    void s03_submittingCreatesAnInProgressRequestWaitingForTheManager() {
        Response created = as("alice").submit(VPN, "  Need to work from home  ");

        assertThat(created.status()).isEqualTo(201);
        JsonNode body = created.body();
        assertThat(body.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(body.path("currentStep").asString()).isEqualTo("MANAGER");
        assertThat(body.path("approvals").isArray()).isTrue();
        assertThat(body.path("approvals").size()).isZero();
        assertThat(body.path("reason").asString()).isEqualTo("Need to work from home");
        assertThat(body.path("accessType").path("name").asString()).isEqualTo("VPN Access");
        assertThat(body.path("requester").path("fullName").asString()).isEqualTo("Alice Anderson");
        assertThat(body.path("createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z");
    }

    @Test
    void s04_submittingTheSameAccessAgainIsADuplicate() {
        ApiClient alice = as("alice");
        alice.submit(VPN, "Need it");

        Response again = alice.submit(VPN, "Need it again");

        assertThat(again.status()).isEqualTo(409);
        assertThat(again.errorCode()).isEqualTo("DUPLICATE_REQUEST");
        assertThat(again.errorMessage()).isEqualTo("You already have a pending request for VPN Access.");
    }

    @Test
    void s05_aUserCannotListApprovals() {
        Response response = as("alice").reviewItems();

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.errorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void s06_adminCannotSeeOrDecideARequestTheManagerHasNotApproved() {
        long id = aliceSubmits(VPN);
        ApiClient admin = as("admin");

        assertThat(ids(admin.reviewItems())).doesNotContain(id);
        Response decide = admin.decide(id, "APPROVED", null);
        assertThat(decide.status()).isEqualTo(404);
        assertThat(decide.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(decide.errorMessage()).isEqualTo("Request not found.");
    }

    @Test
    void s07_anotherTeamsManagerCannotSeeOrDecideTheRequest() {
        long id = aliceSubmits(VPN);
        ApiClient mark = as("mark");

        assertThat(ids(mark.reviewItems())).doesNotContain(id);
        Response decide = mark.decide(id, "APPROVED", null);
        assertThat(decide.status()).isEqualTo(404);
        assertThat(decide.errorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void s08_rejectingWithoutACommentIsInvalid() {
        long id = aliceSubmits(VPN);

        Response response = as("maria").decide(id, "REJECTED", "   ");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.errorMessage()).isEqualTo("Please give a reason for the rejection.");
    }

    @Test
    void s09_managerApprovalMovesTheRequestToTheAdminStep_andCannotBeRepeated() {
        long id = aliceSubmits(VPN);
        ApiClient maria = as("maria");

        Response approved = maria.decide(id, "APPROVED", null);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(approved.body().path("currentStep").asString()).isEqualTo("ADMIN");

        Response again = maria.decide(id, "APPROVED", null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.errorCode()).isEqualTo("NOT_YOUR_STEP");
        assertThat(again.errorMessage()).isEqualTo("This request is waiting for admin approval.");
    }

    @Test
    void s10_adminSeesTheManagerApprovedRequest_andApprovingItFinalizesIt() {
        long id = aliceSubmits(VPN);
        as("maria").decide(id, "APPROVED", "Fine by me");
        ApiClient admin = as("admin");

        JsonNode item = find(admin.reviewItems(), id);
        assertThat(item).isNotNull();
        assertThat(item.path("canAct").asBoolean()).isTrue();

        Response approved = admin.decide(id, "APPROVED", null);
        assertThat(approved.status()).isEqualTo(200);
        JsonNode body = approved.body();
        assertThat(body.path("status").asString()).isEqualTo("APPROVED");
        assertThat(body.path("currentStep").isNull()).isTrue();
        JsonNode approvals = body.path("approvals");
        assertThat(approvals.size()).isEqualTo(2);
        assertThat(approvals.path(0).path("step").asString()).isEqualTo("MANAGER");
        assertThat(approvals.path(0).path("decision").asString()).isEqualTo("APPROVED");
        assertThat(approvals.path(0).path("approver").path("fullName").asString()).isEqualTo("Maria Manager");
        assertThat(approvals.path(0).path("comment").asString()).isEqualTo("Fine by me");
        assertThat(approvals.path(1).path("step").asString()).isEqualTo("ADMIN");
        assertThat(approvals.path(1).path("decision").asString()).isEqualTo("APPROVED");
        assertThat(approvals.path(1).path("approver").path("fullName").asString()).isEqualTo("Adam Admin");
        assertThat(approvals.path(1).has("comment")).isTrue();
        assertThat(approvals.path(1).path("comment").isNull()).isTrue();
    }

    @Test
    void s11_decidingAFinalizedRequestIsRejected() {
        long id = aliceSubmits(VPN);
        as("maria").decide(id, "APPROVED", null);
        ApiClient admin = as("admin");
        admin.decide(id, "APPROVED", null);

        for (ApiClient reviewer : List.of(admin, as("maria"))) {
            Response again = reviewer.decide(id, "REJECTED", "Changed my mind");
            assertThat(again.status()).isEqualTo(409);
            assertThat(again.errorCode()).isEqualTo("ALREADY_FINALIZED");
            assertThat(again.errorMessage()).isEqualTo("This request has already been finalized.");
        }
    }

    @Test
    void s12_aManagerRejectionIsFinal_invisibleToAdmin_andTheAccessCanBeRequestedAgain() {
        ApiClient alice = as("alice");
        long id = alice.submit(FIGMA, "Design review").body().path("id").asLong();

        Response rejected = as("maria").decide(id, "REJECTED", "  Use the shared Figma seat instead.  ");
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.body().path("status").asString()).isEqualTo("REJECTED");
        assertThat(rejected.body().path("currentStep").isNull()).isTrue();
        assertThat(rejected.body().path("approvals").path(0).path("comment").asString())
                .isEqualTo("Use the shared Figma seat instead.");

        assertThat(ids(as("admin").reviewItems())).doesNotContain(id);

        Response again = alice.submit(FIGMA, "Design review, second try");
        assertThat(again.status()).isEqualTo(201);
    }

    @Test
    void s13_requestingAnAlreadyApprovedAccessIsADuplicate() {
        long id = aliceSubmits(VPN);
        as("maria").decide(id, "APPROVED", null);
        as("admin").decide(id, "APPROVED", null);

        Response again = as("alice").submit(VPN, "Need it again");

        assertThat(again.status()).isEqualTo(409);
        assertThat(again.errorCode()).isEqualTo("DUPLICATE_REQUEST");
        assertThat(again.errorMessage()).isEqualTo("You already have VPN Access.");
    }

    @Test
    void s14_aManagerCannotSubmitRequests() {
        Response response = as("maria").submit(VPN, "Need it");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.errorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void s15_dashboardSummaryIsScopedByRole() {
        ApiClient alice = as("alice");
        ApiClient maria = as("maria");
        ApiClient admin = as("admin");

        long vpn = alice.submit(VPN, "Remote work").body().path("id").asLong();
        assertThat(maria.decide(vpn, "APPROVED", null).status()).isEqualTo(200);
        assertThat(admin.decide(vpn, "APPROVED", null).status()).isEqualTo(200);

        long figma = alice.submit(FIGMA, "Design review").body().path("id").asLong();
        assertThat(maria.decide(figma, "REJECTED", "Use the shared seat.").status()).isEqualTo(200);

        assertThat(alice.submit(JIRA, "Sprint planning").status()).isEqualTo(201);

        long github = as("charlie").submit(GITHUB, "Code review").body().path("id").asLong();
        assertThat(as("mark").decide(github, "APPROVED", null).status()).isEqualTo(200);

        Response system = admin.get("/api/dashboard/summary");
        assertThat(system.status()).isEqualTo(200);
        assertThat(fieldNames(system.body())).containsExactlyInAnyOrder("scope", "total", "inProgress",
                "waitingManager", "waitingAdmin", "approved", "rejected", "generatedAt");
        assertThat(Instant.parse(system.body().path("generatedAt").asString())).isNotNull();
        assertSummary(system, "SYSTEM", 4, 2, 1, 1, 1, 1);

        assertSummary(maria.get("/api/dashboard/summary"), "TEAM", 3, 1, 1, 0, 1, 1);
        assertSummary(as("mark").get("/api/dashboard/summary"), "TEAM", 1, 1, 0, 1, 0, 0);
        assertSummary(as("charlie").get("/api/dashboard/summary"), "MINE", 1, 1, 0, 1, 0, 0);
        assertSummary(alice.get("/api/dashboard/summary"), "MINE", 3, 1, 1, 0, 1, 1);
    }

    static void assertSummary(Response response, String scope, long total, long inProgress, long waitingManager,
            long waitingAdmin, long approved, long rejected) {
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.body();
        assertThat(body.path("scope").asString()).isEqualTo(scope);
        assertThat(body.path("total").asLong()).as("total").isEqualTo(total);
        assertThat(body.path("inProgress").asLong()).as("inProgress").isEqualTo(inProgress);
        assertThat(body.path("waitingManager").asLong()).as("waitingManager").isEqualTo(waitingManager);
        assertThat(body.path("waitingAdmin").asLong()).as("waitingAdmin").isEqualTo(waitingAdmin);
        assertThat(body.path("approved").asLong()).as("approved").isEqualTo(approved);
        assertThat(body.path("rejected").asLong()).as("rejected").isEqualTo(rejected);
    }

    private long aliceSubmits(long accessTypeId) {
        Response created = as("alice").submit(accessTypeId, "Need it for work");
        assertThat(created.status()).isEqualTo(201);
        return created.body().path("id").asLong();
    }

    static List<Long> ids(Response list) {
        assertThat(list.status()).isEqualTo(200);
        List<Long> ids = new ArrayList<>();
        list.body().forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    static JsonNode find(Response list, long id) {
        for (JsonNode item : list.body()) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }

    static List<String> fieldNames(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }
}
