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

    @PostMapping
    public ResponseEntity<ServiceModel> createService(@Valid @RequestBody ServiceRequest request) {
        ServiceModel service = serviceManagementService.createService(request);
        return ResponseEntity.ok(service);
    }

    @GetMapping("/branch/main/{branchId}")
    public ResponseEntity<List<ServiceResponseDTO>> getMainServices(@PathVariable String branchId) {
        List<ServiceResponseDTO> services = serviceManagementService.getMainServices(branchId);
        return ResponseEntity.ok(services);
    }

    @GetMapping("/subservices/{parentId}")
    public ResponseEntity<List<ServiceResponseDTO>> getSubServices(@PathVariable String parentId) {
        List<ServiceResponseDTO> services = serviceManagementService.getSubServices(parentId);
        return ResponseEntity.ok(services);
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<ServiceResponseDTO>> getAllServices(@PathVariable String branchId) {
        List<ServiceResponseDTO> services = serviceManagementService.getAllServices(branchId);
        return ResponseEntity.ok(services);
    }
}
