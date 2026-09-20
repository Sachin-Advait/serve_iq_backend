package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.PageResponseDTO;
import com.gis.servelq.dto.RegisterRequest;
import com.gis.servelq.dto.UserResponseDTO;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    // ==================== REGISTRATION ====================

    public User registerUser(RegisterRequest dto, AuthenticatedUser admin) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole() != null ? dto.getRole() : UserRole.USER);
        user.setBranchId(dto.getBranchId());
        user.setFcmToken(dto.getFcmToken());
        user.setCounterId(dto.getCounterId());

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

    // ==================== QUERY METHODS ====================

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
     * Get users with pagination, search, and filters
     */
    public PageResponseDTO<UserResponseDTO> getUsersPaginated(
            int page, int size, String search, String role, String sortBy, String sortDirection) {

        size = size < 1 ? 10 : size;
        page = Math.max(page, 0);

        Sort sort;
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, sortBy);
        } else {
            sort = Sort.by(Sort.Direction.ASC, "name");
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage;

        UserRole roleEnum = null;
        if (role != null && !role.isEmpty() && !"all".equalsIgnoreCase(role)) {
            try {
                roleEnum = UserRole.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        boolean hasSearch = search != null && !search.trim().isEmpty();

        if (hasSearch && roleEnum != null) {
            userPage = userRepository.searchUsers(search.trim(), roleEnum, pageable);
        } else if (hasSearch) {
            userPage = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    search.trim(), search.trim(), pageable);
        } else if (roleEnum != null) {
            userPage = userRepository.findByRole(roleEnum, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        Page<UserResponseDTO> dtoPage = userPage.map(UserResponseDTO::new);
        return new PageResponseDTO<>(dtoPage);
    }

    /**
     * Get users by role with pagination
     */
    public PageResponseDTO<UserResponseDTO> getUsersByRolePaginated(
            UserRole role, int page, int size, String sortBy, String sortDirection) {

        size = size < 1 ? 10 : size;
        page = page < 0 ? 0 : page;

        Sort sort;
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, sortBy);
        } else {
            sort = Sort.by(Sort.Direction.ASC, "name");
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage = userRepository.findByRole(role, pageable);
        Page<UserResponseDTO> dtoPage = userPage.map(UserResponseDTO::new);
        return new PageResponseDTO<>(dtoPage);
    }

    /**
     * Get user statistics
     */
    public Map<String, Object> getUserStats() {
        List<User> allUsers = userRepository.findAll();

        long totalUsers = allUsers.size();
        long activeUsers = allUsers.stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .count();
        long inactiveUsers = totalUsers - activeUsers;
        long adminUsers = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .count();

        Map<String, Long> roleDistribution = allUsers.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getRole().name(),
                        Collectors.counting()
                ));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("inactiveUsers", inactiveUsers);
        stats.put("adminUsers", adminUsers);
        stats.put("roleDistribution", roleDistribution);

        return stats;
    }

    // ==================== UPDATE METHODS ====================

    public User updateUser(String id, User userDetails, AuthenticatedUser currentUser) {
        return userRepository.findById(id).map(user -> {
            User oldUser = cloneUser(user);

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

    public User updateFcmToken(String id, String fcmToken) {
        return userRepository.findById(id).map(user -> {
            user.setFcmToken(fcmToken);
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ==================== DELETE ====================

    public void deleteUser(String id, AuthenticatedUser admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

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

    // ==================== HELPER ====================

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
}