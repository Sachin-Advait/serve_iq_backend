package com.gis.servelq.controllers;

import com.gis.servelq.dto.ServiceRequest;
import com.gis.servelq.dto.ServiceResponseDTO;
import com.gis.servelq.models.ServiceModel;
import com.gis.servelq.services.ServiceManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceManagementService serviceManagementService;

    // CREATE
    @PostMapping
    public ResponseEntity<ServiceModel> createService(@Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.ok(serviceManagementService.createService(request));
    }

    // READ: Get service by ID
    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> getServiceById(@PathVariable String id) {
        return ResponseEntity.ok(serviceManagementService.getServiceById(id));
    }

    // READ: Main services
    @GetMapping("/branch/main/{branchId}")
    public ResponseEntity<List<ServiceResponseDTO>> getMainServices(@PathVariable String branchId) {
        return ResponseEntity.ok(serviceManagementService.getMainServices(branchId));
    }

    // READ: Sub-services
    @GetMapping("/subservices/{parentId}")
    public ResponseEntity<List<ServiceResponseDTO>> getSubServices(@PathVariable String parentId) {
        return ResponseEntity.ok(serviceManagementService.getSubServices(parentId));
    }

    // READ: all services in a branch
    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<ServiceResponseDTO>> getAllServices(@PathVariable String branchId) {
        return ResponseEntity.ok(serviceManagementService.getAllServices(branchId));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ServiceModel> updateService(
            @PathVariable String id,
            @Valid @RequestBody ServiceRequest request
    ) {
        return ResponseEntity.ok(serviceManagementService.updateService(id, request));
    }

    // DELETE (soft delete)
    @PatchMapping("/{id}/disable")
    public ResponseEntity<String> disableService(@PathVariable String id) {
        serviceManagementService.disableService(id);
        return ResponseEntity.ok("Service disabled successfully");
    }

    // DELETE (hard delete)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteService(@PathVariable String id) {
        serviceManagementService.deleteService(id);
        return ResponseEntity.ok("Service deleted permanently");
    }
}
