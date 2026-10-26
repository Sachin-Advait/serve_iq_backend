package com.gis.servelq.security;

import com.gis.servelq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * cache immediately. Cutoffs are stored in UTC so the server timezone doesn't matter.
 */
@Slf4j
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        userRepository.findById(userId).ifPresent(u -> {
            u.setTokensValidAfter(now);
            userRepository.save(u);
        });
        cache.put(userId, new CachedCutoff(Optional.of(now), System.currentTimeMillis()));
    }

    /** Signs every user out: any token issued before now is rejected. */
    public void revokeAllUsers() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        userRepository.revokeAllTokens(now);
        // Other instances pick the new cutoff up when their cache entries expire.
        cache.clear();
    }

    public boolean isRevoked(String userId, Date issuedAt) {
        if (userId == null) return false;
        Optional<LocalDateTime> cutoff = cutoff(userId);
        if (cutoff.isEmpty()) return false;
        if (issuedAt == null) return true;
        LocalDateTime issued = LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneOffset.UTC);
        // A cutoff later than now was written in another timezone (an older build
        // stored server local time, or another server shares the database). Honouring
        // it would reject every new login until that time comes, so ignore it.
        LocalDateTime validAfter = cutoff.get();
        if (validAfter.isAfter(LocalDateTime.now(ZoneOffset.UTC))) return false;
        boolean revoked = issued.isBefore(validAfter);
        if (revoked) {
            log.info("Rejected revoked JWT for user {}: issued {} UTC, valid after {} UTC", userId, issued, validAfter);
        }
        return revoked;
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
