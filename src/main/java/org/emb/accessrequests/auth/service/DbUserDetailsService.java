package org.emb.accessrequests.auth.service;

import java.util.Optional;

import org.emb.accessrequests.auth.security.AppUserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/** Loads users from the database for Spring Security. */
public interface DbUserDetailsService extends UserDetailsService {

    /** Usernames match case-insensitively; the caller trims them. */
    @Override
    AppUserDetails loadUserByUsername(String username);

    Optional<AppUserDetails> loadById(long id);
}
