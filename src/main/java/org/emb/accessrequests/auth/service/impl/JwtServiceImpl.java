package org.emb.accessrequests.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.emb.accessrequests.auth.dto.SessionUser;
import org.emb.accessrequests.auth.service.JwtService;
import org.emb.accessrequests.config.AppProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens. The token only identifies the user (subject = user id);
 * role and manager are reloaded from the database on every request, so changes
 * apply to tokens that are already out.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final JwtEncoder encoder;
    private final Duration expiration;

    public JwtServiceImpl(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.expiration = properties.jwt().expiration();
    }

    @Override
    public Jwt issue(SessionUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .subject(Long.toString(user.id()))
                .claim("username", user.username())
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims));
    }
}
