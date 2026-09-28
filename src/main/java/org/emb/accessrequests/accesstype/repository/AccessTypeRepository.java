package org.emb.accessrequests.accesstype.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.emb.accessrequests.accesstype.entity.AccessType;

public interface AccessTypeRepository extends JpaRepository<AccessType, Long> {
}
