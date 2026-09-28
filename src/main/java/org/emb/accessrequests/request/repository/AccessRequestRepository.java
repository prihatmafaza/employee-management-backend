package org.emb.accessrequests.request.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.emb.accessrequests.request.entity.AccessRequest;
import org.emb.accessrequests.request.entity.Approval;
import org.emb.accessrequests.request.enums.ApprovalStep;
import org.emb.accessrequests.request.enums.Decision;
import org.emb.accessrequests.request.enums.RequestStatus;

/**
 * The finders that feed DTOs fetch the requester, access type and approvals with
 * their approvers in the same query, so building a list costs one query, not N+1.
 */
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    @EntityGraph(attributePaths = {"user", "accessType", "approvals", "approvals.approver"})
    Optional<AccessRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"user", "accessType", "approvals", "approvals.approver"})
    List<AccessRequest> findByUser_IdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "accessType", "approvals", "approvals.approver"})
    @Query("select r from AccessRequest r where r.user.managerId = :managerId order by r.id desc")
    List<AccessRequest> findForManager(Long managerId);

    @EntityGraph(attributePaths = {"user", "accessType", "approvals", "approvals.approver"})
    @Query("""
            select r from AccessRequest r
            where exists (
                select 1 from Approval a
                where a.request = r and a.step = :step and a.decision = :decision)
            order by r.id desc""")
    List<AccessRequest> findWithDecision(ApprovalStep step, Decision decision);

    /** Requests a manager has approved: exactly the ones an admin may see. */
    default List<AccessRequest> findForAdmin() {
        return findWithDecision(ApprovalStep.MANAGER, Decision.APPROVED);
    }

    boolean existsByUser_IdAndAccessType_IdAndStatus(Long userId, Long accessTypeId, RequestStatus status);
}
