package com.gis.servelq.controllers;

import com.gis.servelq.dto.ChangePasswordRequest;
import com.gis.servelq.dto.ChangeRoleRequest;
import com.gis.servelq.dto.UpdateFCMTokenRequest;
import com.gis.servelq.dto.UserResponseDTO;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/serveiq/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers().stream().map(UserResponseDTO::new).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable String id) {
        Optional<User> user = userService.getUser(id);
        return user.map(u -> ResponseEntity.ok(new UserResponseDTO(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Profile and assignment fields. Role and password are handled separately. */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable String id, @RequestBody User user) {
        return ResponseEntity.ok(new UserResponseDTO(userService.updateUser(id, user)));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> changeRole(@PathVariable String id,
                                                      @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(new UserResponseDTO(userService.changeRole(id, request.getRole())));
    }

    /**
     * A user changes their own password and must supply the current one. Note
     * this is under /me so it cannot be pointed at somebody else's account.
     */
    @PostMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                                  @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(caller.id(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    @PatchMapping("/update-fcm-token/{id}")
    public ResponseEntity<UserResponseDTO> updateFcmToken(@RequestBody UpdateFCMTokenRequest req) {
        User updatedUser = userService.updateFcmToken(req.getUserId(), req.getFcmToken());
        return ResponseEntity.ok(new UserResponseDTO(updatedUser));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserResponseDTO>> getUsersByRole(@PathVariable UserRole role) {
        List<UserResponseDTO> usersDto = userService.getUsersByRole(role).stream()
                .map(UserResponseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(usersDto);
    }
}
