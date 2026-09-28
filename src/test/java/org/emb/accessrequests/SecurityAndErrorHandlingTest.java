package org.emb.accessrequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.emb.accessrequests.ApiClient.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Token handling, CSRF-related rules, error format and list semantics beyond the numbered scenarios. */
class SecurityAndErrorHandlingTest extends IntegrationTest {

    // --- Login, bearer token, logout ---

    @Test
    void loginReturnsABearerToken_andSetsNoCookie() {
        Response login = anonymous().post("/api/auth/login", "{\"username\":\"alice\",\"password\":\"password123\"}");

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().path("accessToken").asString()).matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
        assertThat(login.body().path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(login.body().path("expiresIn").asLong()).isPositive();
        assertThat(login.body().path("user").path("username").asString()).isEqualTo("alice");
        assertThat(login.setCookies()).isEmpty();
    }

    @Test
    void usernameIsTrimmedAndCaseInsensitive() {
        Response login = anonymous().post("/api/auth/login", "{\"username\":\"  AlIcE \",\"password\":\"password123\"}");

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().path("user").path("username").asString()).isEqualTo("alice");
    }

    @Test
    void loggingInAgainReplacesTheToken() {
        ApiClient client = as("alice");
        String before = client.accessToken();

        client.login("bob");

        assertThat(client.accessToken()).isNotEqualTo(before);
        assertThat(client.get("/api/auth/me").body().path("username").asString()).isEqualTo("bob");
    }

    @Test
    void malformedOrTamperedTokensAre401() {
        String token = as("alice").accessToken();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(get("/api/auth/me", "Bearer not-a-jwt")).isEqualTo(401);
        assertThat(get("/api/auth/me", "Bearer " + tampered)).isEqualTo(401);
    }

    @Test
    void aStaleTokenDoesNotBlockLogin() {
        ApiClient client = as("alice");
        client.post("/api/auth/logout", null);

        client.login("alice");

        assertThat(client.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void loginWithMissingOrNonStringFieldsIsAValidationError() {
        ApiClient anonymous = anonymous();
        for (String body : List.of("{\"username\":\"alice\"}", "{\"password\":\"password123\"}",
                "{\"username\":42,\"password\":\"password123\"}", "{\"username\":\"alice\",\"password\":true}", "")) {
            Response response = anonymous.post("/api/auth/login", body);
            assertThat(response.status()).as(body).isEqualTo(400);
            assertThat(response.errorCode()).as(body).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void passwordLongerThanBcryptAllowsIsJustAWrongPassword() {
        Response response = anonymous().post("/api/auth/login",
                "{\"username\":\"alice\",\"password\":\"" + "a".repeat(100) + "\"}");

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheToken() {
        ApiClient alice = as("alice");
        String oldToken = alice.accessToken();

        Response logout = alice.post("/api/auth/logout", null);

        assertThat(logout.status()).isEqualTo(204);
        assertThat(get("/api/auth/me", "Bearer " + oldToken)).isEqualTo(401);
    }

    @Test
    void logoutWithoutATokenIsStill204() {
        assertThat(anonymous().post("/api/auth/logout", null).status()).isEqualTo(204);
    }

    @Test
    void repeatedLoginFailuresAreRateLimited() {
        ApiClient anonymous = anonymous();
        for (int i = 0; i < 5; i++) {
            assertThat(anonymous.post("/api/auth/login", "{\"username\":\"bob\",\"password\":\"nope\"}").status())
                    .isEqualTo(401);
        }

        Response blocked = anonymous.post("/api/auth/login", "{\"username\":\"bob\",\"password\":\"password123\"}");
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.errorCode()).isEqualTo("RATE_LIMITED");

        // Other usernames are unaffected.
        assertThat(anonymous().login("alice").get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void roleChangesApplyToExistingTokensImmediately() {
        ApiClient maria = as("maria");
        assertThat(maria.reviewItems().status()).isEqualTo(200);
        try {
            jdbc.update("UPDATE users SET role = 'USER' WHERE username = 'maria'");

            assertThat(maria.reviewItems().status()).isEqualTo(403);
            assertThat(maria.get("/api/auth/me").body().path("role").asString()).isEqualTo("USER");
        } finally {
            jdbc.update("UPDATE users SET role = 'MANAGER' WHERE username = 'maria'");
        }
    }

    // --- Content type and body rules ---

    @Test
    void nonJsonBodiesAreRejectedWith415() {
        ApiClient maria = as("maria");
        for (String contentType : List.of("text/plain", "application/x-www-form-urlencoded", "multipart/form-data; boundary=x")) {
            Response response = maria.post("/api/requests/1/decision", contentType, "decision=APPROVED");
            assertThat(response.status()).as(contentType).isEqualTo(415);
            assertThat(response.errorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        }
        // Even endpoints that ignore their body, and even without a token.
        assertThat(anonymous().post("/api/auth/login", "text/plain", "{}").status()).isEqualTo(415);
        assertThat(maria.post("/api/auth/logout", "text/plain", "x").status()).isEqualTo(415);
    }

    @Test
    void jsonWithACharsetIsAccepted() {
        Response response = anonymous().post("/api/auth/login", "application/json; charset=utf-8",
                "{\"username\":\"alice\",\"password\":\"password123\"}");

        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    void oversizedBodiesAreRejectedWith413() {
        Response response = anonymous().post("/api/auth/login",
                "{\"username\":\"" + "a".repeat(20_000) + "\",\"password\":\"x\"}");

        assertThat(response.status()).isEqualTo(413);
        assertThat(response.body().path("error").path("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    // --- Error format for Spring's own errors ---

    @Test
    void malformedJsonIsAValidationError() {
        Response response = as("alice").post("/api/requests", "{\"accessTypeId\": 1, \"reason\": ");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void nonIntegerIdIsAValidationError() {
        Response response = as("maria").decide(0, "APPROVED", null);
        assertThat(response.status()).isEqualTo(404);

        Response notANumber = as("maria").post("/api/requests/abc/decision", "{\"decision\":\"APPROVED\"}");
        assertThat(notANumber.status()).isEqualTo(400);
        assertThat(notANumber.errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unknownRoutesAreJson404s() {
        Response response = as("alice").get("/api/does-not-exist");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(response.raw().headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/json"));
    }

    @Test
    void wrongMethodIsAJsonError() {
        Response response = as("maria").send("DELETE", "/api/approvals");

        assertThat(response.status()).isEqualTo(405);
        assertThat(response.errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void usersCannotDecideAndReviewersCannotListMine() {
        Response aliceDecides = as("alice").decide(1, "APPROVED", null);
        assertThat(aliceDecides.status()).isEqualTo(403);

        assertThat(as("maria").get("/api/requests/mine").status()).isEqualTo(403);
        assertThat(as("admin").get("/api/requests/mine").status()).isEqualTo(403);
    }

    // --- Lists ---

    @Test
    void accessTypesAreListedById() {
        Response response = as("bob").get("/api/access-types");

        assertThat(AcceptanceScenariosTest.ids(response)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(response.body().path(1).path("name").asString()).isEqualTo("GitHub / GitLab Access");
    }

    @Test
    void myRequestsAreOnlyMine_newestFirst() {
        ApiClient alice = as("alice");
        long first = alice.submit(VPN, "one").body().path("id").asLong();
        long second = alice.submit(JIRA, "two").body().path("id").asLong();
        as("bob").submit(VPN, "bob's");

        assertThat(AcceptanceScenariosTest.ids(alice.get("/api/requests/mine"))).containsExactly(second, first);
    }

    @Test
    void managersSeeTheirTeamInEveryStatus_withCanActOnlyAtTheirStep() {
        ApiClient alice = as("alice");
        ApiClient maria = as("maria");
        long waiting = alice.submit(VPN, "a").body().path("id").asLong();
        long atAdmin = alice.submit(GITHUB, "b").body().path("id").asLong();
        long rejected = as("bob").submit(FIGMA, "c").body().path("id").asLong();
        long otherTeam = as("charlie").submit(VPN, "d").body().path("id").asLong();
        maria.decide(atAdmin, "APPROVED", null);
        maria.decide(rejected, "REJECTED", "No");

        Response items = maria.reviewItems();

        assertThat(AcceptanceScenariosTest.ids(items)).containsExactly(rejected, atAdmin, waiting);
        assertThat(canAct(items, waiting)).isTrue();
        assertThat(canAct(items, atAdmin)).isFalse();
        assertThat(canAct(items, rejected)).isFalse();
        assertThat(AcceptanceScenariosTest.ids(as("mark").reviewItems())).containsExactly(otherTeam);
    }

    private static boolean canAct(Response items, long id) {
        JsonNode item = AcceptanceScenariosTest.find(items, id);
        assertThat(item).as("item %d", id).isNotNull();
        return item.path("canAct").asBoolean();
    }

    private int get(String path, String authorization) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Authorization", authorization).GET().build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
