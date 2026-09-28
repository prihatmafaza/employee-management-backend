package org.emb.accessrequests.dashboard.service;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.dashboard.dto.DashboardSummaryDto;

/** Request counts for the Dashboard page. */
public interface DashboardService {

    /** Counts by status and step; which requests are counted depends only on the caller's role. */
    DashboardSummaryDto summary(SessionUser me);
}
