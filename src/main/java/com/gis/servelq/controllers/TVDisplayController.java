package com.gis.servelq.controllers;

import com.gis.servelq.dto.TVDisplayResponseDTO;
import com.gis.servelq.services.TVDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tv-display")
@RequiredArgsConstructor
public class TVDisplayController {
    private final TVDisplayService tvDisplayService;

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<TVDisplayResponseDTO> getTVDisplayData(@PathVariable String branchId) {
        TVDisplayResponseDTO response = tvDisplayService.getTVDisplayData(branchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/branch/{branchId}/latest-calls")
    public ResponseEntity<TVDisplayResponseDTO> getLatestCalls(@PathVariable String branchId) {
        TVDisplayResponseDTO response = tvDisplayService.getTVDisplayData(branchId);
        // Return only latest calls for real-time updates
        TVDisplayResponseDTO limitedResponse = new TVDisplayResponseDTO();
        limitedResponse.setLatestCalls(response.getLatestCalls());
        return ResponseEntity.ok(limitedResponse);
    }
}