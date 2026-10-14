package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.PageResponseDTO;
import com.gis.servelq.dto.UserSessionDTO;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.services.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Check-in / check-out history of agents, admins and receptionists.
 * Example: /serveiq/api/admin/user-sessions?role=USER&startDate=2026-10-01&endDate=2026-10-14
 */
@RestController
@RequestMapping("/serveiq/api/admin/user-sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class UserSessionController {

    private final UserSessionService userSessionService;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<UserSessionDTO>>> getSessions(
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Login history retrieved successfully",
                userSessionService.search(branchId, userId, role, startDate, endDate, page, size)));
    }
}
