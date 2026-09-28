package org.emb.accessrequests.dashboard.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.dashboard.dto.DashboardSummaryDto;
import org.emb.accessrequests.dashboard.dto.SummaryScope;
import org.emb.accessrequests.dashboard.repository.DashboardRepository;
import org.emb.accessrequests.dashboard.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboard;

    public DashboardServiceImpl(DashboardRepository dashboard) {
        this.dashboard = dashboard;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryDto summary(SessionUser me) {
        Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return switch (me.role()) {
            // Intentionally wider than what an admin can see in GET /api/approvals (only
            // manager-approved requests): this returns counts only, never request details,
            // so it doesn't break the visibility rules.
            case ADMIN -> DashboardSummaryDto.from(SummaryScope.SYSTEM, dashboard.summaryAll(), generatedAt);
            case MANAGER -> DashboardSummaryDto.from(SummaryScope.TEAM, dashboard.summaryForManager(me.id()), generatedAt);
            case USER -> DashboardSummaryDto.from(SummaryScope.MINE, dashboard.summaryForUser(me.id()), generatedAt);
        };
    }
}
