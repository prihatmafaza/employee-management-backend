package org.emb.accessrequests.dashboard.controller;

import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.dashboard.dto.DashboardSummaryDto;
import org.emb.accessrequests.dashboard.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The scope comes only from the caller's role; there are deliberately no query parameters. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryDto summary(@AuthenticationPrincipal AppUserDetails principal) {
        return dashboardService.summary(principal.user());
    }
}
