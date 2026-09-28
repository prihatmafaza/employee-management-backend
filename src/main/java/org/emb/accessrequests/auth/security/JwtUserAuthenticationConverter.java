package org.emb.accessrequests.auth.security;

import org.emb.accessrequests.auth.service.DbUserDetailsService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a verified token into an {@link AppUserDetails} principal, reloading the
 * user from the database on every request. A role change therefore applies to
 * tokens already issued, and a deleted user's token stops working.
 */
@Component
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final DbUserDetailsService userDetailsService;

    public JwtUserAuthenticationConverter(DbUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        AppUserDetails user = userDetailsService.loadById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        user.eraseCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(user, jwt, user.getAuthorities());
    }
}
