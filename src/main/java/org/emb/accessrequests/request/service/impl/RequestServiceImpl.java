package org.emb.accessrequests.request.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.emb.accessrequests.accesstype.entity.AccessType;
import org.emb.accessrequests.accesstype.repository.AccessTypeRepository;
import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.error.ApiException;
import org.emb.accessrequests.error.ErrorCodes;
import org.emb.accessrequests.error.GlobalExceptionHandler;
import org.emb.accessrequests.request.dto.RequestDtos.AccessRequestDto;
import org.emb.accessrequests.request.dto.RequestDtos.ReviewItemDto;
import org.emb.accessrequests.request.entity.AccessRequest;
import org.emb.accessrequests.request.entity.Approval;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.request.enums.RequestStatus;
import org.emb.accessrequests.request.repository.AccessRequestRepository;
import org.emb.accessrequests.request.repository.ApprovalRepository;
import org.emb.accessrequests.request.service.RequestRules;
import org.emb.accessrequests.request.service.RequestService;
import org.emb.accessrequests.user.entity.User;
import org.emb.accessrequests.user.enums.Role;
import org.emb.accessrequests.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The request workflow. Every check from the contract happens here, in the
 * contract's order, including visibility: the URL rules in SecurityConfig only
 * gate endpoints by role.
 */
@Service
public class RequestServiceImpl implements RequestService {

    private static final String ONE_ACTIVE_PER_TYPE = "access_requests_one_active_per_type";
    private static final String ONE_DECISION_PER_STEP = "approvals_request_step_key";

    private final AccessRequestRepository requests;
    private final ApprovalRepository approvals;
    private final AccessTypeRepository accessTypes;
    private final UserRepository users;

    public RequestServiceImpl(AccessRequestRepository requests, ApprovalRepository approvals,
            AccessTypeRepository accessTypes, UserRepository users) {
        this.requests = requests;
        this.approvals = approvals;
        this.accessTypes = accessTypes;
        this.users = users;
    }

    @Transactional(readOnly = true)
    @Override
    public List<AccessRequestDto> listMine(SessionUser me) {
        return requests.findByUser_IdOrderByIdDesc(me.id()).stream().map(AccessRequestDto::from).toList();
    }

    @Transactional
    @Override
    public AccessRequestDto submit(SessionUser me, Long accessTypeId, String rawReason) {
        requireRole(me, Role.USER);
        String reason = rawReason == null ? "" : rawReason.strip();
        if (reason.isEmpty()) {
            throw ApiException.validation("Please enter a reason for the request.");
        }
        if (reason.length() > MAX_TEXT_LENGTH) {
            throw ApiException.validation("Reason must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        AccessType accessType = accessTypeId == null ? null : accessTypes.findById(accessTypeId).orElse(null);
        if (accessType == null) {
            throw ApiException.validation("Please select a valid access.");
        }
        if (me.managerId() == null) {
            throw ApiException.validation("You have no manager assigned, so your request cannot be approved.");
        }
        if (requests.existsByUser_IdAndAccessType_IdAndStatus(me.id(), accessTypeId, RequestStatus.APPROVED)) {
            throw ApiException.conflict(ErrorCodes.DUPLICATE_REQUEST,
                    "You already have " + accessType.getName() + ".");
        }
        if (requests.existsByUser_IdAndAccessType_IdAndStatus(me.id(), accessTypeId, RequestStatus.IN_PROGRESS)) {
            throw pendingDuplicate(accessType);
        }

        User requester = users.findById(me.id()).orElseThrow();
        AccessRequest request = new AccessRequest(requester, accessType, reason, now());
        try {
            requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a simultaneous submission of the same access.
            if (violates(e, ONE_ACTIVE_PER_TYPE)) {
                throw pendingDuplicate(accessType);
            }
            throw e;
        }
        return AccessRequestDto.from(request);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ReviewItemDto> listReviewItems(SessionUser me) {
        List<AccessRequest> candidates = switch (me.role()) {
            case MANAGER -> requests.findForManager(me.id());
            case ADMIN -> requests.findForAdmin();
            case USER -> throw forbidden();
        };
        return candidates.stream()
                .filter(r -> RequestRules.isVisibleToReviewer(me, r))
                .map(r -> new ReviewItemDto(AccessRequestDto.from(r), RequestRules.canAct(me, r)))
                .toList();
    }

    /**
     * Records the caller's decision and advances the request, in one transaction.
     * Two reviewers deciding at once can't both succeed: the loser either hits the
     * unique (request_id, step) constraint or the request's version check, and gets 409.
     */
    @Transactional
    @Override
    public AccessRequestDto decide(SessionUser me, long requestId, String rawDecision, String rawComment) {
        if (me.role() != Role.MANAGER && me.role() != Role.ADMIN) {
            throw forbidden();
        }
        Decision decision = parseDecision(rawDecision);
        String comment = rawComment == null ? "" : rawComment.strip();
        if (comment.length() > MAX_TEXT_LENGTH) {
            throw ApiException.validation("Comment must be at most " + MAX_TEXT_LENGTH + " characters.");
        }

        AccessRequest request = requests.findWithDetailsById(requestId)
                .filter(r -> RequestRules.isVisibleToReviewer(me, r))
                .orElseThrow(() -> ApiException.notFound("Request not found."));
        if (request.getStatus() != RequestStatus.IN_PROGRESS) {
            throw ApiException.conflict(ErrorCodes.ALREADY_FINALIZED, "This request has already been finalized.");
        }
        if (!RequestRules.canAct(me, request)) {
            throw ApiException.conflict(ErrorCodes.NOT_YOUR_STEP,
                    request.getCurrentStep() == ApprovalStep.ADMIN
                            ? "This request is waiting for admin approval."
                            : "This request is still waiting for manager approval.");
        }
        if (decision == Decision.REJECTED && comment.isEmpty()) {
            throw ApiException.validation("Please give a reason for the rejection.");
        }

        User approver = users.findById(me.id()).orElseThrow();
        Approval approval = request.decide(approver, decision, comment.isEmpty() ? null : comment, now());
        try {
            approvals.save(approval);   // INSERT approval
            requests.flush();           // UPDATE access_requests ... WHERE id = ? AND version = ?
        } catch (OptimisticLockingFailureException e) {
            throw concurrentDecision();
        } catch (DataIntegrityViolationException e) {
            if (violates(e, ONE_DECISION_PER_STEP)) {
                throw concurrentDecision();
            }
            throw e;
        }
        return AccessRequestDto.from(request);
    }

    private static Decision parseDecision(String raw) {
        if (raw != null) {
            for (Decision d : Decision.values()) {
                if (d.name().equals(raw)) {
                    return d;
                }
            }
        }
        throw ApiException.validation("Decision must be APPROVED or REJECTED.");
    }

    private static void requireRole(SessionUser me, Role role) {
        if (me.role() != role) {
            throw forbidden();
        }
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, ErrorCodes.FORBIDDEN_MESSAGE);
    }

    private static ApiException pendingDuplicate(AccessType accessType) {
        return ApiException.conflict(ErrorCodes.DUPLICATE_REQUEST,
                "You already have a pending request for " + accessType.getName() + ".");
    }

    private static ApiException concurrentDecision() {
        return ApiException.conflict(ErrorCodes.ALREADY_FINALIZED, GlobalExceptionHandler.CONCURRENT_DECISION_MESSAGE);
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && constraint.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    // Postgres keeps microseconds; the contract's examples (and JS Dates) use milliseconds.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
