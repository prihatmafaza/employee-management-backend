package org.emb.accessrequests.auth.dto;

import java.io.Serializable;

import org.emb.accessrequests.auth.security.JwtUserAuthenticationConverter;
import org.emb.accessrequests.user.entity.User;
import org.emb.accessrequests.user.enums.Role;

/**
 * The logged-in user as services see it. It is rebuilt from the database on
 * every request (see {@link JwtUserAuthenticationConverter}), so role and
 * manager changes take effect immediately.
 */
public record SessionUser(long id, String username, String fullName, Role role, Long managerId)
        implements Serializable {

    public static SessionUser from(User user) {
        return new SessionUser(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getManagerId());
    }
}
