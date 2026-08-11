package com.gis.servelq.services;

import com.gis.servelq.dto.ServiceRequest;
import com.gis.servelq.dto.ServiceResponseDTO;
import com.gis.servelq.dto.ServiceUpdateRequest;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.Services;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceManagementService {

    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    public Services createService(ServiceRequest serviceRequest, AuthenticatedUser user) {
        serviceRepository.findByCodeAndBranchId(serviceRequest.getCode(), serviceRequest.getBranchId())
                .ifPresent(s -> {
                    throw new RuntimeException("Service code already exists");
                });

        branchRepository.findById(serviceRequest.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch does not exist"));

        Services parent = null;
        if (serviceRequest.getParentId() != null) {
            parent = serviceRepository.findById(serviceRequest.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent service does not exist"));
        }

        Services newService = new Services();

        newService.setCode(serviceRequest.getCode());
        newService.setName(serviceRequest.getName());
        newService.setArabicName(serviceRequest.getArabicName());
        newService.setParentId(serviceRequest.getParentId());
        newService.setEnabled(serviceRequest.getEnabled());
        newService.setBranchId(serviceRequest.getBranchId());
        newService.setCounterIds(serviceRequest.getCounterIds());

        newService = serviceRepository.save(newService);

        if (parent != null) {
            List<String> childList = parent.getChildren();

            if (childList == null || childList.isEmpty()) {
                childList = new java.util.ArrayList<>();
            }

            childList.add(newService.getId());
            parent.setChildren(childList);

            serviceRepository.save(parent);
        }

        // Log service created
        auditLogService.log(
                AuditAction.SERVICE_CREATED,
                "Service",
                newService.getId(),
                newService.getName(),
                "Service created: " + newService.getName() + " (" + newService.getCode() + ")",
                user,
                newService.getBranchId(),
                this.request
        );

        return newService;
    }

    // READ: Get by ID
    public ServiceResponseDTO getServiceById(String id) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        return ServiceResponseDTO.fromEntity(service);
    }

    // READ: Main
    public List<ServiceResponseDTO> getMainServices(String branchId) {
        return serviceRepository.findMainServicesByBranchId(branchId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    // READ: Sub
    public List<ServiceResponseDTO> getSubServices(String parentId) {
        return serviceRepository.findByParentIdAndEnabledTrue(parentId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    // READ: ADMIN API
    public List<ServiceResponseDTO> getAllServices(String branchId) {
        return serviceRepository.findByBranchId(branchId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    public Services updateService(String id, ServiceUpdateRequest updateRequest, AuthenticatedUser user) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        // Save old state for audit
        Services oldService = cloneService(service);

        // Null-safe code uniqueness check
        if (updateRequest.getCode() != null && !updateRequest.getCode().equals(service.getCode())) {
            serviceRepository.findByCodeAndBranchId(updateRequest.getCode(), service.getBranchId()).ifPresent(s -> {
                throw new RuntimeException("Service code already exists");
            });

            service.setCode(updateRequest.getCode());
        }

        // Update only non-null fields
        if (updateRequest.getName() != null) service.setName(updateRequest.getName());
        if (updateRequest.getArabicName() != null) service.setArabicName(updateRequest.getArabicName());
        if (updateRequest.getParentId() != null) service.setParentId(updateRequest.getParentId());
        if (updateRequest.getCounterIds() != null) service.setCounterIds(updateRequest.getCounterIds());
        if (updateRequest.getEnabled() != null) service.setEnabled(updateRequest.getEnabled());
        if (updateRequest.getBranchId() != null) service.setBranchId(updateRequest.getBranchId());

        Services updatedService = serviceRepository.save(service);

        // Log service updated
        auditLogService.logWithChanges(
                AuditAction.SERVICE_UPDATED,
                "Service",
                updatedService.getId(),
                updatedService.getName(),
                "Service updated: " + updatedService.getName(),
                oldService,
                updatedService,
                user,
                updatedService.getBranchId(),
                request
        );

        return updatedService;
    }

    // SOFT DELETE
    public void disableService(String id, AuthenticatedUser user) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        service.setEnabled(false);
        serviceRepository.save(service);

        // Log service disabled
        auditLogService.log(
                AuditAction.SERVICE_UPDATED,
                "Service",
                service.getId(),
                service.getName(),
                "Service disabled: " + service.getName(),
                user,
                service.getBranchId(),
                request
        );
    }

    // HARD DELETE
    public void deleteService(String id, AuthenticatedUser user) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        // Log before deletion
        auditLogService.log(
                AuditAction.SERVICE_DELETED,
                "Service",
                service.getId(),
                service.getName(),
                "Service deleted: " + service.getName(),
                user,
                service.getBranchId(),
                request
        );

        serviceRepository.deleteById(id);
    }

    // Helper method to clone service for audit comparison
    private Services cloneService(Services original) {
        Services clone = new Services();
        clone.setId(original.getId());
        clone.setCode(original.getCode());
        clone.setName(original.getName());
        clone.setArabicName(original.getArabicName());
        clone.setParentId(original.getParentId());
        clone.setEnabled(original.getEnabled());
        clone.setCounterIds(original.getCounterIds());
        clone.setChildren(original.getChildren());
        clone.setBranchId(original.getBranchId());
        return clone;
    }
}