package com.gis.servelq.controllers;

import com.gis.servelq.services.ReportMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Sends the scheduled report e-mail now, to check the mail settings.
 * Admin panel staff only via the /serveiq/api/admin/** rule in SecurityConfig.
 * Example: POST /serveiq/api/admin/report-mail/send?from=2026-10-27&to=2026-10-27
 * (both optional; default is yesterday).
 */
@RestController
@RequestMapping("/serveiq/api/admin/report-mail")
@RequiredArgsConstructor
public class ReportMailController {

    private final ReportMailService reportMailService;

    @PostMapping("/send")
    public ResponseEntity<ReportMailService.Result> send(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        LocalDate end = to != null ? to : LocalDate.now().minusDays(1);
        LocalDate start = from != null ? from : end;
        return ResponseEntity.ok(reportMailService.send(start, end));
    }
}
