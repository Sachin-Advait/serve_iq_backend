package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.RegisterRequest;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private UserRepository userRepository;

    public User registerUser(RegisterRequest dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // Registration is admin only now (see SecurityConfig), but still default
        // to USER when no role is given rather than trusting whatever arrives.
        user.setRole(dto.getRole() != null ? dto.getRole() : UserRole.USER);
        user.setBranchId(dto.getBranchId());
        user.setFcmToken(dto.getFcmToken());
        user.setCounterId(dto.getCounterId());

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUser(String id) {
        return userRepository.findById(id);
    }

    /**
     * Profile and assignment fields only.
     *
     * Role and password used to be settable here off a raw User body, so a PUT
     * to any user id could promote that account to ADMIN or overwrite its
     * password - account takeover with no credentials. Those two now go through
     * changeRole and changePassword.
     */
    public User updateUser(String id, User userDetails) {
        return userRepository.findById(id).map(user -> {
            if (userDetails.getName() != null) user.setName(userDetails.getName());
            if (userDetails.getBranchId() != null) user.setBranchId(userDetails.getBranchId());
            if (userDetails.getCounterId() != null) user.setCounterId(userDetails.getCounterId());

            return userRepository.save(user);
        }).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public User changeRole(String id, UserRole newRole) {
        return userRepository.findById(id).map(user -> {
            user.setRole(newRole);
            return userRepository.save(user);
        }).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** The caller has to prove they know the current password. */
    public void changePassword(String id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("New password must be at least 8 characters");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public User updateFcmToken(String id, String fcmToken) {
        return userRepository.findById(id).map(user -> {
            user.setFcmToken(fcmToken);
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }
}
