package com.gis.servelq.controllers;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately does nothing but return 200. Used by clients as a latency
 * probe (round-trip time to this endpoint approximates real network
 * conditions) — it must stay free of DB calls, auth checks, or anything
 * else that could itself be slow, or it stops measuring the network and
 * starts measuring the app.
 */
@RestController
@RequestMapping("/serveiq/api/health-check")
public class HealthCheckController {

    @GetMapping
    public ResponseEntity<Void> healthCheck() {
        return ResponseEntity.ok().build();
    }
}