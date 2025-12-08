package com.gis.servelq.controllers;

import com.gis.servelq.dto.LoginRequest;
import com.gis.servelq.dto.RegisterRequest;
import com.gis.servelq.dto.UserResponseDTO;
import com.gis.servelq.models.User;
import com.gis.servelq.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public UserResponseDTO register(@RequestBody RegisterRequest dto) {
        User user = userService.registerUser(dto);
        UserResponseDTO response = new UserResponseDTO();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setBranchId(user.getBranchId());
        response.setCounterId(user.getCounterId());
        return response;
    }

    @PostMapping("/login")
    public UserResponseDTO login(@RequestBody LoginRequest dto) {
        User user = userService.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!dto.getPassword().matches(user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        UserResponseDTO response = new UserResponseDTO();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setBranchId(user.getBranchId());
        response.setCounterId(user.getCounterId());
        return response;
    }
}
