package org.emb.accessrequests.auth.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.emb.accessrequests.auth.service.LoginRateLimiter;
import org.emb.accessrequests.config.AppProperties;
import org.emb.accessrequests.error.ApiException;
import org.emb.accessrequests.error.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins per (username, client IP) in memory. After too many
 * failures within the window, further attempts get 429 until the window ends.
 * A successful login clears the counter. Single-instance only, by design.
 */
@Component
public class LoginRateLimiterImpl implements LoginRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Failures(Instant windowStart, int count) {
    }

    private final int maxFailures;
    private final Duration window;
    private final ConcurrentHashMap<String, Failures> failures = new ConcurrentHashMap<>();

    public LoginRateLimiterImpl(AppProperties properties) {
        this.maxFailures = properties.loginRateLimit().maxFailures();
        this.window = properties.loginRateLimit().window();
    }

    @Override
    public void checkAllowed(String key) {
        Failures current = failures.get(key);
        if (current != null && !isExpired(current) && current.count() >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMITED,
                    "Too many failed login attempts. Please wait a few minutes and try again.");
        }
    }

    @Override
    public void recordFailure(String key) {
        failures.compute(key, (k, current) -> current == null || isExpired(current)
                ? new Failures(Instant.now(), 1)
                : new Failures(current.windowStart(), current.count() + 1));
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.values().removeIf(this::isExpired);
        }
    }

    @Override
    public void reset(String key) {
        failures.remove(key);
    }

    /** Forgets all counters (used by tests). */
    @Override
    public void clear() {
        failures.clear();
    }

    private boolean isExpired(Failures f) {
        return f.windowStart().plus(window).isBefore(Instant.now());
    }
}
