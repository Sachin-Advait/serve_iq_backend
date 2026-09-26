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
import org.springframework.util.StringUtils;
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

    @PostMapping("/register")
    public UserResponseDTO register(@Valid @RequestBody RegisterRequest dto,
                                    @AuthenticationPrincipal AuthenticatedUser admin) {
        return new UserResponseDTO(userService.registerUser(dto, admin));
    }

    @GetMapping("/counters")
    public List<CounterOptionDTO> counters() {
        return counterService.getPublicCounterList();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest dto) {

        AdAuthService.AuthResult authResult = adAuthService.authenticate(dto.getEmail(), dto.getPassword());

        if (!authResult.isSuccess()) {
            auditLogService.log(
                    AuditAction.LOGIN_FAILED, "User", null, dto.getEmail(),
                    "Failed login attempt for email: " + dto.getEmail(),
                    null, null, request);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = authResult.getUser();

        if (user.getRole() == UserRole.USER) {
            if (!StringUtils.hasText(dto.getCounterId())) {
                return ResponseEntity.badRequest().body("Please select a counter");
            }
            AuthenticatedUser actor = new AuthenticatedUser(
                    user.getId(), user.getEmail(), user.getRole().name(),
                    user.getBranchId(), user.getCounterId());
            try {
                user = counterService.claimCounter(dto.getCounterId(), false, actor);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        if (dto.getFcmToken() != null && user.getRole() != UserRole.ADMIN) {
            user.setFcmToken(dto.getFcmToken());
            userRepository.save(user);
        }

        auditLogService.log(
                AuditAction.LOGIN, "User", user.getId(), user.getName(),
                "User logged in successfully",
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name(),
                        user.getBranchId(), user.getCounterId()),
                user.getBranchId(), request);

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponseDTO(
                token, jwtService.getExpirationMinutes() * 60, new UserResponseDTO(user)));
    }
}