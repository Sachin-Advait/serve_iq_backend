package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.RegisterRequest;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    public User registerUser(RegisterRequest dto, AuthenticatedUser admin) {
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

        // Fixed: Save first, then audit with saved user
        User savedUser = userRepository.save(user);

        auditLogService.log(
                AuditAction.USER_CREATED,
                "User",
                savedUser.getId(),
                savedUser.getName(),
                "User account created: " + savedUser.getName() + " with role " + savedUser.getRole(),
                admin,
                savedUser.getBranchId(),
                request
        );

        return savedUser;
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
    public User updateUser(String id, User userDetails, AuthenticatedUser currentUser) {
        return userRepository.findById(id).map(user -> {
            User oldUser = cloneUser(user); // Save old state for audit

            if (userDetails.getName() != null) user.setName(userDetails.getName());
            if (userDetails.getBranchId() != null) user.setBranchId(userDetails.getBranchId());
            if (userDetails.getCounterId() != null) user.setCounterId(userDetails.getCounterId());

            User updatedUser = userRepository.save(user);

            auditLogService.logWithChanges(
                    AuditAction.USER_UPDATED,
                    "User",
                    updatedUser.getId(),
                    updatedUser.getName(),
                    "User profile updated: " + updatedUser.getName(),
                    oldUser,
                    updatedUser,
                    currentUser,
                    updatedUser.getBranchId(),
                    request
            );

            return updatedUser;
        }).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public User changeRole(String id, UserRole newRole, AuthenticatedUser admin) {
        return userRepository.findById(id).map(user -> {
            UserRole oldRole = user.getRole();
            user.setRole(newRole);
            User updatedUser = userRepository.save(user);

            auditLogService.log(
                    AuditAction.ROLE_CHANGED,
                    "User",
                    updatedUser.getId(),
                    updatedUser.getName(),
                    "User role changed from " + oldRole + " to " + newRole + " for user: " + updatedUser.getName(),
                    admin,
                    updatedUser.getBranchId(),
                    request
            );

            return updatedUser;
        }).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** The caller has to prove they know the current password. */
    public void changePassword(String id, String currentPassword, String newPassword,
                               AuthenticatedUser currentUser) {
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

        auditLogService.log(
                AuditAction.PASSWORD_CHANGED,
                "User",
                id,
                currentUser.email(),
                "Password changed for user: " + currentUser.email(),
                currentUser,
                currentUser.branchId(),
                request
        );
    }

    public User updateFcmToken(String id, String fcmToken) {
        return userRepository.findById(id).map(user -> {
            user.setFcmToken(fcmToken);
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void deleteUser(String id, AuthenticatedUser admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Log before deletion
        auditLogService.log(
                AuditAction.USER_DELETED,
                "User",
                user.getId(),
                user.getName(),
                "User account deleted: " + user.getName() + " (" + user.getEmail() + ")",
                admin,
                user.getBranchId(),
                request
        );

        userRepository.deleteById(id);
    }

    // Helper method to clone user for audit comparison
    private User cloneUser(User original) {
        User clone = new User();
        clone.setId(original.getId());
        clone.setName(original.getName());
        clone.setEmail(original.getEmail());
        clone.setRole(original.getRole());
        clone.setBranchId(original.getBranchId());
        clone.setCounterId(original.getCounterId());
        clone.setFcmToken(original.getFcmToken());
        return clone;
    }

    public void resetPassword(String id, String newPassword, AuthenticatedUser admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("New password must be at least 8 characters");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditLogService.log(
                AuditAction.PASSWORD_CHANGED,
                "User",
                id,
                user.getName(),
                "Password reset by admin: " + admin.email() + " for user: " + user.getName(),
                admin,
                user.getBranchId(),
                request
        );
    }
}