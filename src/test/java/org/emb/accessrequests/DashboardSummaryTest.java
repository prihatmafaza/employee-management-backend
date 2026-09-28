package org.emb.accessrequests;

import static org.emb.accessrequests.AcceptanceScenariosTest.assertSummary;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Dashboard summary cases beyond acceptance scenario 15. */
class DashboardSummaryTest extends IntegrationTest {

    @Test
    void withNoRequestsEveryCountIsZero_neverNull() {
        Map<String, String> scopes = Map.of("admin", "SYSTEM", "maria", "TEAM", "alice", "MINE");
        scopes.forEach((username, scope) ->
                assertSummary(as(username).get("/api/dashboard/summary"), scope, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void aUserWithoutRequestsSeesZeros_evenWhenTheirTeamHasSome() {
        ApiClient alice = as("alice");
        alice.submit(VPN, "Remote work");
        alice.submit(JIRA, "Sprint planning");

        assertSummary(as("bob").get("/api/dashboard/summary"), "MINE", 0, 0, 0, 0, 0, 0);
        assertSummary(as("maria").get("/api/dashboard/summary"), "TEAM", 2, 2, 2, 0, 0, 0);
    }
}
