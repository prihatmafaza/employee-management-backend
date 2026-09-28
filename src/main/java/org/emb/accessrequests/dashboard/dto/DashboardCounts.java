package org.emb.accessrequests.dashboard.dto;

/** The six request counts, read together in one aggregate query. */
public record DashboardCounts(long total, long inProgress, long waitingManager, long waitingAdmin,
        long approved, long rejected) {
}
