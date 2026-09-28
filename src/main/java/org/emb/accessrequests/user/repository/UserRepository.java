package org.emb.accessrequests.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.emb.accessrequests.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);
}
