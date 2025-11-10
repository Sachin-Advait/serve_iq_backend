package com.gis.servelq.controllers;

import com.gis.servelq.dto.TVDisplayResponse;
import com.gis.servelq.services.TVDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tv-display")
@RequiredArgsConstructor
public class TVDisplayController {
    private final TVDisplayService tvDisplayService;

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<TVDisplayResponse> getTVDisplayData(@PathVariable String branchId) {
        TVDisplayResponse response = tvDisplayService.getTVDisplayData(branchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/branch/{branchId}/latest-calls")
    public ResponseEntity<TVDisplayResponse> getLatestCalls(@PathVariable String branchId) {
        TVDisplayResponse response = tvDisplayService.getTVDisplayData(branchId);
        // Return only latest calls for real-time updates
        TVDisplayResponse limitedResponse = new TVDisplayResponse();
        limitedResponse.setLatestCalls(response.getLatestCalls());
        return ResponseEntity.ok(limitedResponse);
    }
}