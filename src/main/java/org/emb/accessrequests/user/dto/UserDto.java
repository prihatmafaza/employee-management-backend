package org.emb.accessrequests.user.dto;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.user.entity.User;
import org.emb.accessrequests.user.enums.Role;

/** The contract's {@code User}. Never carries the password hash. */
public record UserDto(long id, String username, String fullName, Role role, Long managerId) {

    public static UserDto from(SessionUser user) {
        return new UserDto(user.id(), user.username(), user.fullName(), user.role(), user.managerId());
    }
}
