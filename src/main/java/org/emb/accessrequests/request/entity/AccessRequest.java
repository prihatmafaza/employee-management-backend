package org.emb.accessrequests.request.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.emb.accessrequests.accesstype.entity.AccessType;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.request.enums.RequestStatus;
import org.emb.accessrequests.user.entity.User;

@Entity
@Table(name = "access_requests")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The requester. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_type_id", nullable = false)
    private AccessType accessType;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** The step the request is waiting on; null exactly when the request is final. */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_step")
    private ApprovalStep currentStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    // Approvals are only ever inserted MANAGER first, then ADMIN, so id order is step order.
    @OneToMany(mappedBy = "request")
    @OrderBy("id ASC")
    private List<Approval> approvals = new ArrayList<>();

    protected AccessRequest() {
    }

    public AccessRequest(User requester, AccessType accessType, String reason, Instant now) {
        this.user = requester;
        this.accessType = accessType;
        this.reason = reason;
        this.status = RequestStatus.IN_PROGRESS;
        this.currentStep = ApprovalStep.MANAGER;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Records a decision on the current step and moves the request on:
     * <pre>
     * MANAGER + APPROVED -> IN_PROGRESS / ADMIN
     * MANAGER + REJECTED -> REJECTED / null
     * ADMIN   + APPROVED -> APPROVED / null
     * ADMIN   + REJECTED -> REJECTED / null
     * </pre>
     * The caller is responsible for checking that {@code approver} may decide this step.
     *
     * @return the new approval, which the caller must persist
     */
    public Approval decide(User approver, Decision decision, String comment, Instant now) {
        if (status != RequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Request " + id + " is already " + status);
        }
        Approval approval = new Approval(this, currentStep, approver, decision, comment, now);
        approvals.add(approval);
        if (decision == Decision.REJECTED) {
            status = RequestStatus.REJECTED;
            currentStep = null;
        } else if (currentStep == ApprovalStep.MANAGER) {
            currentStep = ApprovalStep.ADMIN;
        } else {
            status = RequestStatus.APPROVED;
            currentStep = null;
        }
        updatedAt = now;
        return approval;
    }

    /** True once the requester's manager has approved the request. */
    public boolean hasManagerApproval() {
        return approvals.stream().anyMatch(
                a -> a.getStep() == ApprovalStep.MANAGER && a.getDecision() == Decision.APPROVED);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public String getReason() {
        return reason;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public ApprovalStep getCurrentStep() {
        return currentStep;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Approval> getApprovals() {
        return Collections.unmodifiableList(approvals);
    }
}
