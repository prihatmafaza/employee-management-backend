package org.emb.accessrequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.emb.accessrequests.ApiClient.Response;
import org.junit.jupiter.api.Test;

/** Two reviewers deciding the same step at the same moment: exactly one wins. */
class DecisionConcurrencyTest extends IntegrationTest {

    @Test
    void twoAdminsApprovingTheSameRequestInParallel_exactlyOneSucceeds() throws Exception {
        // Two separate admin sessions. Repeat a few times to give the race a chance to happen.
        ApiClient admin1 = as("admin");
        ApiClient admin2 = as("admin");
        for (long accessTypeId : List.of(VPN, GITHUB, FIGMA, JIRA)) {
            long id = as("alice").submit(accessTypeId, "Race " + accessTypeId).body().path("id").asLong();
            assertThat(as("maria").decide(id, "APPROVED", null).status()).isEqualTo(200);

            List<Response> results = race(
                    () -> admin1.decide(id, "APPROVED", null),
                    () -> admin2.decide(id, "REJECTED", "Not needed"));

            assertThat(results).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
            assertThat(results).filteredOn(r -> r.status() == 409)
                    .extracting(Response::errorCode).containsExactly("ALREADY_FINALIZED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM approvals WHERE request_id = ? AND step = 'ADMIN'",
                    Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    void aManagerDoubleSubmittingADecision_recordsItOnce() throws Exception {
        ApiClient tab1 = as("maria");
        ApiClient tab2 = as("maria");
        long id = as("alice").submit(VPN, "Race").body().path("id").asLong();

        List<Response> results = race(
                () -> tab1.decide(id, "APPROVED", null),
                () -> tab2.decide(id, "APPROVED", null));

        assertThat(results).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approvals WHERE request_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_step FROM access_requests WHERE id = ?", String.class, id))
                .isEqualTo("ADMIN");
    }

    @SafeVarargs
    private static List<Response> race(Supplier<Response>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (Supplier<Response> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.get();
                }));
            }
            start.countDown();
            List<Response> results = new ArrayList<>();
            for (Future<Response> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
