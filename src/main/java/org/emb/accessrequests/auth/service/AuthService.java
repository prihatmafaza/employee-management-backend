package org.emb.accessrequests.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.emb.accessrequests.auth.dto.LoginBody;
import org.emb.accessrequests.auth.dto.LoginResponse;
import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.user.dto.UserDto;

/** Stateless JWT login and logout. */
public interface AuthService {

    /** Checks the credentials (rate limited per username and IP) and issues an access token. */
    LoginResponse login(LoginBody body, HttpServletRequest request);

    /**
     * Revokes the bearer token sent with the request, if it is still valid. A
     * missing, expired or invalid token is ignored, so logging out is always safe.
     */
    void logout(HttpServletRequest request);

    UserDto me(AppUserDetails principal);
}
