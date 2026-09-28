package org.emb.accessrequests.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("10240") long maxBodyBytes,
        @DefaultValue LoginRateLimit loginRateLimit,
        Jwt jwt) {

    public record LoginRateLimit(
            @DefaultValue("5") int maxFailures,
            @DefaultValue("15m") Duration window) {
    }

    /** HS256 signing secret (at least 32 bytes) and how long an access token stays valid. */
    public record Jwt(
            String secret,
            @DefaultValue("2h") Duration expiration) {
    }
}
