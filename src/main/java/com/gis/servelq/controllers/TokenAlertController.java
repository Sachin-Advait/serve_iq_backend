package com.gis.servelq.controllers;

import com.gis.servelq.dto.TokenAlertSettingsDTO;
import com.gis.servelq.services.TokenAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Waiting-token alert mail settings for the admin panel's System page.
 * Admin panel staff only via the /serveiq/api/admin/** rule in SecurityConfig.
 */
@RestController
@RequestMapping("/serveiq/api/admin/token-alert")
@RequiredArgsConstructor
public class TokenAlertController {

    private final TokenAlertService tokenAlertService;

    @GetMapping("/settings")
    public ResponseEntity<TokenAlertSettingsDTO> settings() {
        return ResponseEntity.ok(tokenAlertService.settingsDto());
    }

    @PutMapping("/settings")
    public ResponseEntity<TokenAlertSettingsDTO> update(@RequestBody TokenAlertSettingsDTO request) {
        return ResponseEntity.ok(tokenAlertService.updateSettings(request));
    }

    /** Sends a test alert for the branch to the chosen roles now. */
    @PostMapping("/test")
    public ResponseEntity<TokenAlertService.Result> test(@RequestParam String branchId) {
        return ResponseEntity.ok(tokenAlertService.sendTest(branchId));
    }
}
