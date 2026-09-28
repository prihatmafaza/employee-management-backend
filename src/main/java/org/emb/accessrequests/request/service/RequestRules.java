package org.emb.accessrequests.request.service;

import java.util.Objects;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.request.entity.AccessRequest;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.RequestStatus;
import org.emb.accessrequests.user.enums.Role;

/** Who may see and decide which request. Pure functions, so they're easy to unit test. */
public final class RequestRules {

    private RequestRules() {
    }

    /**
     * Whether a reviewer can see a request: a MANAGER sees their team's requests in
     * any status; an ADMIN sees only requests a manager has approved.
     */
    public static boolean isVisibleToReviewer(SessionUser reviewer, AccessRequest request) {
        return switch (reviewer.role()) {
            case MANAGER -> Objects.equals(request.getUser().getManagerId(), reviewer.id());
            case ADMIN -> request.hasManagerApproval();
            case USER -> false;
        };
    }

    /** Whether a reviewer who can see a request may decide it right now. */
    public static boolean canAct(SessionUser reviewer, AccessRequest request) {
        return request.getStatus() == RequestStatus.IN_PROGRESS
                && request.getCurrentStep() != null
                && request.getCurrentStep() == stepFor(reviewer.role());
    }

    /** The approval step a role decides, or null for roles that decide none. */
    public static ApprovalStep stepFor(Role role) {
        return switch (role) {
            case MANAGER -> ApprovalStep.MANAGER;
            case ADMIN -> ApprovalStep.ADMIN;
            case USER -> null;
        };
    }
}
