package org.emb.accessrequests.dashboard.dto;

import java.time.Instant;

public record DashboardSummaryDto(SummaryScope scope, long total, long inProgress, long waitingManager,
        long waitingAdmin, long approved, long rejected, Instant generatedAt) {

    public static DashboardSummaryDto from(SummaryScope scope, DashboardCounts counts, Instant generatedAt) {
        return new DashboardSummaryDto(scope, counts.total(), counts.inProgress(), counts.waitingManager(),
                counts.waitingAdmin(), counts.approved(), counts.rejected(), generatedAt);
    }
}
