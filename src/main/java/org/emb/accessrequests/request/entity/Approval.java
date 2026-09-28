package org.emb.accessrequests.request.entity;

import java.time.Instant;

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
import jakarta.persistence.Table;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.user.entity.User;

/** One reviewer's decision on one step of a request. Never changed once written. */
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private AccessRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStep step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected Approval() {
    }

    Approval(AccessRequest request, ApprovalStep step, User approver, Decision decision, String comment,
            Instant decidedAt) {
        this.request = request;
        this.step = step;
        this.approver = approver;
        this.decision = decision;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public AccessRequest getRequest() {
        return request;
    }

    public ApprovalStep getStep() {
        return step;
    }

    public User getApprover() {
        return approver;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
