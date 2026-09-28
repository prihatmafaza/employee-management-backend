package org.emb.accessrequests.auth.service;

import java.time.Instant;

/** Token ids revoked by logout. */
public interface TokenDenylist {

    /** Revokes the token until {@code expiresAt}, after which it is invalid anyway. */
    void revoke(String tokenId, Instant expiresAt);

    boolean isRevoked(String tokenId);
}
