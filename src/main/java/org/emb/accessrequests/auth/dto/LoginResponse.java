package org.emb.accessrequests.auth.dto;

import org.emb.accessrequests.user.dto.UserDto;

/** Sent back on login. The client sends {@code accessToken} as {@code Authorization: Bearer <token>}. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {

    public static LoginResponse bearer(String accessToken, long expiresIn, UserDto user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }
}
