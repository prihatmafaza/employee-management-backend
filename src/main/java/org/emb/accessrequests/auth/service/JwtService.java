package org.emb.accessrequests.auth.service;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.springframework.security.oauth2.jwt.Jwt;

/** Issues access tokens. */
public interface JwtService {

    /** A signed token whose subject is the user's id, valid for {@code app.jwt.expiration}. */
    Jwt issue(SessionUser user);
}
