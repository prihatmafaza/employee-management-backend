package org.emb.accessrequests.auth.service.impl;

import java.util.Optional;

import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.auth.service.DbUserDetailsService;
import org.emb.accessrequests.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsServiceImpl implements DbUserDetailsService {

    private final UserRepository users;

    public DbUserDetailsServiceImpl(UserRepository users) {
        this.users = users;
    }

    /** Usernames match case-insensitively; the caller trims them. */
    @Override
    public AppUserDetails loadUserByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Override
    public Optional<AppUserDetails> loadById(long id) {
        return users.findById(id).map(AppUserDetails::new);
    }
}
