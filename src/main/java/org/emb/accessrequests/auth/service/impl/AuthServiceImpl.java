package org.emb.accessrequests.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import org.emb.accessrequests.auth.dto.LoginBody;
import org.emb.accessrequests.auth.dto.LoginResponse;
import org.emb.accessrequests.auth.security.AppUserDetails;
import org.emb.accessrequests.auth.service.AuthService;
import org.emb.accessrequests.auth.service.JwtService;
import org.emb.accessrequests.auth.service.LoginRateLimiter;
import org.emb.accessrequests.auth.service.TokenDenylist;
import org.emb.accessrequests.error.ApiException;
import org.emb.accessrequests.user.dto.UserDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Service;

/** Stateless JWT login and logout, with per (username, IP) rate limiting on failures. */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password.";

    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final JwtDecoder jwtDecoder;
    private final TokenDenylist denylist;
    private final BearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();

    public AuthServiceImpl(AuthenticationManager authenticationManager, LoginRateLimiter rateLimiter,
            JwtService jwtService, JwtDecoder jwtDecoder, TokenDenylist denylist) {
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.jwtService = jwtService;
        this.jwtDecoder = jwtDecoder;
        this.denylist = denylist;
    }

    @Override
    public LoginResponse login(LoginBody body, HttpServletRequest request) {
        if (body.username() == null || body.password() == null) {
            throw ApiException.validation("Please enter your username and password.");
        }
        String username = body.username().strip();
        String rateLimitKey = username.toLowerCase(Locale.ROOT) + "|" + request.getRemoteAddr();
        rateLimiter.checkAllowed(rateLimitKey);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, body.password()));
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(rateLimitKey);
            throw ApiException.unauthenticated(INVALID_CREDENTIALS);
        }
        rateLimiter.reset(rateLimitKey);

        AppUserDetails principal = (AppUserDetails) Objects.requireNonNull(authentication.getPrincipal());
        Jwt token = jwtService.issue(principal.user());
        long expiresIn = Duration.between(Instant.now(), Objects.requireNonNull(token.getExpiresAt())).toSeconds();
        return LoginResponse.bearer(token.getTokenValue(), expiresIn, UserDto.from(principal.user()));
    }

    /**
     * Revokes the bearer token sent with the request, if it is still valid. A
     * missing, expired or invalid token is ignored, so logging out is always safe.
     */
    @Override
    public void logout(HttpServletRequest request) {
        String tokenValue;
        try {
            tokenValue = bearerTokenResolver.resolve(request);
        } catch (AuthenticationException e) {
            return;
        }
        if (tokenValue == null) {
            return;
        }
        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            denylist.revoke(jwt.getId(), jwt.getExpiresAt());
        } catch (JwtException e) {
            // Already unusable: nothing to revoke.
        }
    }

    @Override
    public UserDto me(AppUserDetails principal) {
        return UserDto.from(principal.user());
    }
}
