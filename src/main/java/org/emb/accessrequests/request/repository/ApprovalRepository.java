package org.emb.accessrequests.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.emb.accessrequests.request.entity.Approval;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
}
