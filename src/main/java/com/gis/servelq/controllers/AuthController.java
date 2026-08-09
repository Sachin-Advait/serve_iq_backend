package com.gis.servelq.controllers;

import com.gis.servelq.dto.LoginRequest;
import com.gis.servelq.dto.LoginResponseDTO;
import com.gis.servelq.dto.RegisterRequest;
import com.gis.servelq.dto.UserResponseDTO;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.JwtService;
import com.gis.servelq.services.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/serveiq/api/auth")
@AllArgsConstructor
public class AuthController {

    private final PasswordEncoder passwordEncoder;
    private UserService userService;
    private UserRepository userRepository;
    private JwtService jwtService;

    /** Admin only - see SecurityConfig. */
    @PostMapping("/register")
    public UserResponseDTO register(@Valid @RequestBody RegisterRequest dto) {
        return new UserResponseDTO(userService.registerUser(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequest dto) {
        Optional<User> found = userService.findByEmail(dto.getEmail());

        // Same response for "no such account" and "wrong password". These used
        // to throw different messages, which made login an account enumeration
        // endpoint.
        if (found.isEmpty()
                || !passwordEncoder.matches(dto.getPassword(), found.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = found.get();
        if (dto.getFcmToken() != null && user.getRole() != UserRole.ADMIN) {
            user.setFcmToken(dto.getFcmToken());
            userRepository.save(user);
        }

        return ResponseEntity.ok(new LoginResponseDTO(
                jwtService.generateToken(user),
                jwtService.getExpirationMinutes() * 60,
                new UserResponseDTO(user)));
    }
}
