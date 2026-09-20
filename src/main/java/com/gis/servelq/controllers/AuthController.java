package com.gis.servelq.controllers;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.security.JwtService;
import com.gis.servelq.services.AdAuthService;
import com.gis.servelq.services.AuditLogService;
import com.gis.servelq.services.CounterService;
import com.gis.servelq.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/serveiq/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;
    private final AdAuthService adAuthService;
    private final CounterService counterService;
    private final JwtService jwtService;

    /**
     * Admin only - see SecurityConfig.
     */
    @PostMapping("/register")
    public UserResponseDTO register(@Valid @RequestBody RegisterRequest dto,
                                    @AuthenticationPrincipal AuthenticatedUser admin) {
        return new UserResponseDTO(userService.registerUser(dto, admin));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequest dto) {

        // Try AD first, then PostgreSQL fallback
        AdAuthService.AuthResult authResult = adAuthService.authenticate(dto.getEmail(), dto.getPassword());

        if (!authResult.isSuccess()) {
            // Log failed login attempt
            auditLogService.log(
                    AuditAction.LOGIN_FAILED,
                    "User",
                    null,
                    dto.getEmail(),
                    "Failed login attempt for email: " + dto.getEmail(),
                    null,
                    null,
                    request
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = authResult.getUser();

        // Update FCM token if provided
        if (dto.getFcmToken() != null && user.getRole() != UserRole.ADMIN) {
            user.setFcmToken(dto.getFcmToken());
            userRepository.save(user);
        }

        // Log successful login
        auditLogService.log(
                AuditAction.LOGIN,
                "User",
                user.getId(),
                user.getName(),
                "User logged in successfully",
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name(),
                        user.getBranchId(), user.getCounterId()),
                user.getBranchId(),
                request
        );

        return ResponseEntity.ok(new LoginResponseDTO(
                authResult.getToken(),
                authResult.getExpiresIn(),
                new UserResponseDTO(user)));
    }

    @GetMapping("/counter-options")
    public List<CounterOptionDTO> counterOptions(@AuthenticationPrincipal AuthenticatedUser current) {
        return counterService.getCounterOptions(current);
    }

    @PostMapping("/counter-login")
    public ResponseEntity<LoginResponseDTO> counterLogin(@Valid @RequestBody CounterLoginRequest dto,
                                                         @AuthenticationPrincipal AuthenticatedUser current) {
        User user = counterService.claimCounter(dto.getCounterId(), dto.isAttachOnly(), current);
        String token = jwtService.generateToken(user);   // user.counterId is now the chosen counter
        return ResponseEntity.ok(new LoginResponseDTO(
                token, jwtService.getExpirationMinutes() * 60, new UserResponseDTO(user)));
    }
}