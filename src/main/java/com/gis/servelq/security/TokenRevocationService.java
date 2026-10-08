package com.gis.servelq.security;

import com.gis.servelq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets the server invalidate a user's login tokens. Agent JWTs never expire,
 * so after an admin force-releases a counter the agent's app kept working
 * with its old token and stayed stuck on that counter. Revoking stamps
 * users.tokens_valid_after; any token issued before it is rejected.
 *
 * The cutoff is cached per user for a short time so the JWT filter doesn't
 * hit the database on every request. A revoke on this instance updates the
 * cache immediately.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final long CACHE_TTL_MILLIS = 15_000;

    private final UserRepository userRepository;
    private final Map<String, CachedCutoff> cache = new ConcurrentHashMap<>();

    private record CachedCutoff(Optional<LocalDateTime> validAfter, long loadedAt) {
    }

    public void revokeAll(String userId) {
        // JWT iat has second precision, so cut at the second: a token issued
        // in an earlier second is rejected, a login after this is accepted.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        userRepository.findById(userId).ifPresent(u -> {
            u.setTokensValidAfter(now);
            userRepository.save(u);
        });
        cache.put(userId, new CachedCutoff(Optional.of(now), System.currentTimeMillis()));
    }

    /** Signs every user out: any token issued before now is rejected. */
    public void revokeAllUsers() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        userRepository.revokeAllTokens(now);
        // Other instances pick the new cutoff up when their cache entries expire.
        cache.clear();
    }

    public boolean isRevoked(String userId, Date issuedAt) {
        if (userId == null) return false;
        Optional<LocalDateTime> cutoff = cutoff(userId);
        if (cutoff.isEmpty()) return false;
        if (issuedAt == null) return true;
        LocalDateTime issued = LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());
        return issued.isBefore(cutoff.get());
    }

    private Optional<LocalDateTime> cutoff(String userId) {
        long now = System.currentTimeMillis();
        CachedCutoff cached = cache.get(userId);
        if (cached != null && now - cached.loadedAt() < CACHE_TTL_MILLIS) {
            return cached.validAfter();
        }
        Optional<LocalDateTime> loaded = userRepository.findById(userId)
                .map(u -> u.getTokensValidAfter());
        cache.put(userId, new CachedCutoff(loaded, now));
        return loaded;
    }
}
