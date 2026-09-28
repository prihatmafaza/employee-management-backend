package org.emb.accessrequests.auth.service;

/** Limits failed logins per key (username and client IP). */
public interface LoginRateLimiter {

    /** Throws a 429 {@code ApiException} if the key has too many recent failures. */
    void checkAllowed(String key);

    void recordFailure(String key);

    /** Clears the key's counter after a successful login. */
    void reset(String key);

    /** Forgets all counters (used by tests). */
    void clear();
}
