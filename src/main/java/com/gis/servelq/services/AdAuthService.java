package com.gis.servelq.services;

import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.JwtService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdAuthService {

    private final ActiveDirectoryService adService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CounterRepository counterRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;  // For local DB password check

    /**
     * Try AD authentication FIRST, then fallback to PostgreSQL
     */
    public AuthResult authenticate(String username, String password) {

        // Step 1: Try AD Authentication FIRST
        log.info("Trying AD authentication for: {}", username);

        if (adService.authenticate(username, password)) {
            log.info("AD authentication successful for: {}", username);
            return handleAdLogin(username);
        }

        log.info("AD authentication failed for: {}, trying PostgreSQL", username);

        // Step 2: Fallback to PostgreSQL (Local DB)
        return handlePostgresLogin(username, password);
    }

    /**
     * Handle successful AD authentication - create/update user in PostgreSQL
     */
    private AuthResult handleAdLogin(String username) {
        // Get user details from AD
        Map<String, String> adAttrs = adService.getUserAttributes(username);
        String email = adAttrs.get("email");
        String displayName = adAttrs.get("displayName");

        if (email == null || email.isEmpty()) {
            return AuthResult.failed("User not found in Active Directory");
        }

        // Find or create user in PostgreSQL
        Optional<User> dbUserOpt = userRepository.findByEmail(email.toLowerCase());

        User user = dbUserOpt.orElseGet(() -> {
            // Create new user from AD
            User newUser = new User();
            newUser.setName(displayName);
            newUser.setEmail(email.toLowerCase());
            newUser.setRole(UserRole.USER);
            newUser.setActive(true);
            newUser.setPassword("AD_AUTH");  // Placeholder - auth goes through AD
            return newUser;
        });

        // Update name if changed
        if (displayName != null) {
            user.setName(displayName);
        }

        // Set branch if not already set
        assignDefaultBranchIfNeeded(user);

        // Save user
        User savedUser = userRepository.save(user);

        // Check active
        if (Boolean.FALSE.equals(savedUser.getActive())) {
            return AuthResult.failed("Account is deactivated");
        }

        // Reset the user's assigned counter to IDLE/unpaused on every login
        activateUserCounter(savedUser);

        // Generate JWT
        String token = jwtService.generateToken(savedUser);
        long expiresIn = jwtService.getExpirationMinutes() * 60;

        log.info("AD login successful for: {}", email);
        return AuthResult.success(token, expiresIn, savedUser);
    }

    /**
     * Fallback to PostgreSQL local user authentication
     */
    private AuthResult handlePostgresLogin(String email, String password) {
        Optional<User> dbUserOpt = userRepository.findByEmail(email.toLowerCase());

        if (dbUserOpt.isEmpty()) {
            log.warn("User not found in PostgreSQL: {}", email);
            return AuthResult.failed("Invalid credentials");
        }

        User user = dbUserOpt.get();

        // Check if this is a local user (not AD user)
        if ("AD_AUTH".equals(user.getPassword())) {
            // This is an AD user but AD auth failed - cannot login
            log.warn("AD user tried local login but AD auth failed: {}", email);
            return AuthResult.failed("Invalid credentials");
        }

        // Verify password with BCrypt
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Password mismatch for local user: {}", email);
            return AuthResult.failed("Invalid credentials");
        }

        // Check active
        if (Boolean.FALSE.equals(user.getActive())) {
            return AuthResult.failed("Account is deactivated");
        }

        // Set branch if not already set
        assignDefaultBranchIfNeeded(user);
        user = userRepository.save(user);

        // Reset the user's assigned counter to IDLE/unpaused on every login
        activateUserCounter(user);

        // Generate JWT
        String token = jwtService.generateToken(user);
        long expiresIn = jwtService.getExpirationMinutes() * 60;

        log.info("PostgreSQL login successful for: {}", email);
        return AuthResult.success(token, expiresIn, user);
    }

    /**
     * On successful login, put the user's assigned counter (if any) back into
     * a serviceable state: enabled counters go to IDLE with paused=false, so
     * an agent who logged out mid-break/mid-call doesn't come back to a
     * counter still stuck showing their last status.
     * <p>
     * Best-effort: a missing/already-deleted counter just logs and moves on,
     * it should never block login.
     */
    private void activateUserCounter(User user) {
        // Counter agents get their counter state handled by CounterService.claimCounter
        // (which preserves a counter that is mid-call/serving). Resetting it here would
        // flip a busy counter to IDLE before the claim runs.
        if (user.getRole() == UserRole.USER) {
            return;
        }
        String counterId = user.getCounterId();
        if (counterId == null || counterId.isBlank()) {
            return;
        }

        counterRepository.findById(counterId).ifPresentOrElse(counter -> {
            counter.setStatus(CounterStatus.IDLE);
            counter.setPaused(false);
            counterRepository.save(counter);
            log.info("Counter {} reset to IDLE/unpaused on login for user {}",
                    counterId, user.getEmail());
        }, () -> log.warn("User {} references missing counter {} on login",
                user.getEmail(), counterId));
    }

    private void assignDefaultBranchIfNeeded(User user) {
        if (user.getBranchId() == null || user.getBranchId().isEmpty()) {
            branchRepository.findFirstByOrderByCreatedAtAsc()
                    .ifPresent(branch -> user.setBranchId(branch.getId()));
        }
    }

    @Data
    @AllArgsConstructor
    public static class AuthResult {
        private boolean success;
        private String message;
        private String token;
        private long expiresIn;
        private User user;

        public static AuthResult failed(String message) {
            return new AuthResult(false, message, null, 0, null);
        }

        public static AuthResult success(String token, long expiresIn, User user) {
            return new AuthResult(true, "Login successful", token, expiresIn, user);
        }
    }
}