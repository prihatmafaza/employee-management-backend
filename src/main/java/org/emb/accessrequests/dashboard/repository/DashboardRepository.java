package org.emb.accessrequests.dashboard.repository;

import org.emb.accessrequests.dashboard.dto.DashboardCounts;
import org.emb.accessrequests.request.entity.AccessRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Dashboard counts. Each method is a single aggregate query (PostgreSQL
 * {@code count(*) FILTER (WHERE ...)}), so all six numbers come from one snapshot
 * and no request is loaded into memory. {@code count} over zero rows is 0, never null.
 *
 * <p>Three methods with a fixed WHERE rather than one with an optional caller id:
 * PostgreSQL can't infer the type of a null parameter, and each variant gets its own
 * plan ({@code access_requests(user_id)} for MINE).
 */
public interface DashboardRepository extends Repository<AccessRequest, Long> {

    String COUNTS = """
            select new org.emb.accessrequests.dashboard.dto.DashboardCounts(
                count(*),
                count(*) filter (where r.status = IN_PROGRESS),
                count(*) filter (where r.status = IN_PROGRESS and r.currentStep = MANAGER),
                count(*) filter (where r.status = IN_PROGRESS and r.currentStep = ADMIN),
                count(*) filter (where r.status = APPROVED),
                count(*) filter (where r.status = REJECTED))
            from AccessRequest r
            """;

    @Query(COUNTS)
    DashboardCounts summaryAll();

    @Query(COUNTS + "where r.user.managerId = :managerId")
    DashboardCounts summaryForManager(long managerId);

    @Query(COUNTS + "where r.user.id = :userId")
    DashboardCounts summaryForUser(long userId);
}
