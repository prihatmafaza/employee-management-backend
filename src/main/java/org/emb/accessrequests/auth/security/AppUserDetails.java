package org.emb.accessrequests.auth.security;

import java.util.Collection;
import java.util.List;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.user.entity.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security's view of a {@link User}. The password hash is only held
 * during login and erased afterwards, so it never leaves the login check.
 */
public class AppUserDetails implements UserDetails, CredentialsContainer {

    private final SessionUser user;
    private String passwordHash;

    public AppUserDetails(User user) {
        this.user = SessionUser.from(user);
        this.passwordHash = user.getPasswordHash();
    }

    public SessionUser user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
