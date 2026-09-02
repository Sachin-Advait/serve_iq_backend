package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.services.AdSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/serveiq/api/admin/ad-sync")
@RequiredArgsConstructor
public class AdSyncController {

    private final AdSyncService adSyncService;

    /**
     * Manual AD sync trigger
     * POST /serveiq/api/admin/ad-sync/manual
     */
    @PostMapping("/manual")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<Map<String, Integer>>> manualSync() {
        log.info("=== Manual AD sync triggered via API ===");

        Map<String, Integer> result = adSyncService.manualSync();

        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(
                    new ApiResponseDTO<>(false, "AD sync failed - no users fetched", result)
            );
        }

        return ResponseEntity.ok(
                new ApiResponseDTO<>(true, "AD sync completed successfully", result)
        );
    }
}