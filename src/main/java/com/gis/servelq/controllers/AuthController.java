package com.gis.servelq.controllers;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.User;
import com.gis.servelq.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public UserResponse register(@RequestBody RegisterRequest dto) {
        User user = userService.registerUser(dto);
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setBranchId(user.getBranchId());
        return response;
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest dto) {
        User user = userService.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!dto.getPassword().matches(user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setBranchId(user.getBranchId());
        return response;
    }
}
