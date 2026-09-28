package org.emb.accessrequests.dashboard.dto;

/** Which requests a dashboard summary counts, decided by the caller's role. */
public enum SummaryScope {
    /** ADMIN: every request. */
    SYSTEM,
    /** MANAGER: requests from the manager's team. */
    TEAM,
    /** USER: the caller's own requests. */
    MINE
}
