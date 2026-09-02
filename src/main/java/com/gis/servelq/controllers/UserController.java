package com.gis.servelq.controllers;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/serveiq/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get all users with optional pagination, search, and filters
     * No params → returns full list (backward compatible)
     */
    @GetMapping
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {

        boolean hasParams = search != null || role != null || sortBy != null
                || page > 0 || size != 10;

        if (!hasParams) {
            // Legacy: return full list
            List<UserResponseDTO> allUsers = userService.getAllUsers().stream()
                    .map(UserResponseDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(allUsers);
        }

        // Paginated
        PageResponseDTO<UserResponseDTO> result = userService.getUsersPaginated(
                page, size, search, role, sortBy, sortDirection);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Users fetched successfully", result));
    }

    /**
     * Get user statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> getUserStats() {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "User stats fetched successfully",
                userService.getUserStats()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable String id) {
        Optional<User> user = userService.getUser(id);
        return user.map(u -> ResponseEntity.ok(new UserResponseDTO(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable String id,
                                                      @RequestBody User user,
                                                      @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(new UserResponseDTO(userService.updateUser(id, user, currentUser)));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<UserResponseDTO> changeRole(@PathVariable String id,
                                                      @Valid @RequestBody ChangeRoleRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(new UserResponseDTO(userService.changeRole(id, request.getRole(), admin)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetUserPassword(@PathVariable String id,
                                                  @Valid @RequestBody ResetPasswordRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser admin) {
        userService.resetPassword(id, request.getNewPassword(), admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                                  @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(caller.id(), request.getCurrentPassword(), request.getNewPassword(), caller);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable String id,
                                             @AuthenticationPrincipal AuthenticatedUser admin) {
        userService.deleteUser(id, admin);
        return ResponseEntity.ok("User deleted successfully");
    }

    @PatchMapping("/update-fcm-token/{id}")
    public ResponseEntity<UserResponseDTO> updateFcmToken(@PathVariable String id,
                                                          @RequestBody UpdateFCMTokenRequest req) {
        User updatedUser = userService.updateFcmToken(id, req.getFcmToken());
        return ResponseEntity.ok(new UserResponseDTO(updatedUser));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<String>> getAllRoles() {
        List<String> roles = Arrays.stream(UserRole.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }

    /**
     * Get users by role with optional pagination
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<?> getUsersByRole(
            @PathVariable UserRole role,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {

        boolean hasParams = page > 0 || size != 10 || sortBy != null;

        if (!hasParams) {
            // Legacy behavior
            List<UserResponseDTO> usersDto = userService.getUsersByRole(role).stream()
                    .map(UserResponseDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(usersDto);
        }

        // Paginated
        PageResponseDTO<UserResponseDTO> result = userService.getUsersByRolePaginated(
                role, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Users fetched successfully", result));
    }
}