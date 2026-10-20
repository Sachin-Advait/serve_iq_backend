package com.gis.servelq.controllers;

import com.gis.servelq.dto.TransferReasonRequest;
import com.gis.servelq.models.TransferReason;
import com.gis.servelq.services.TransferReasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET is open to any logged-in user (the agent app lists the active reasons);
 * changes are ADMIN/MANAGER only, see SecurityConfig.
 */
@RestController
@RequestMapping("/serveiq/api/transfer-reasons")
@RequiredArgsConstructor
public class TransferReasonController {

    private final TransferReasonService transferReasonService;

    @GetMapping
    public ResponseEntity<List<TransferReason>> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(transferReasonService.findAll(includeInactive));
    }

    @PostMapping
    public ResponseEntity<TransferReason> create(@RequestBody TransferReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferReasonService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransferReason> update(@PathVariable String id, @RequestBody TransferReasonRequest request) {
        return ResponseEntity.ok(transferReasonService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean removed = transferReasonService.delete(id);
        return ResponseEntity.ok(Map.of(
                "removed", removed,
                "message", removed
                        ? "Transfer reason deleted"
                        : "This reason is used by earlier transfers, so it was switched off instead of deleted"));
    }
}
