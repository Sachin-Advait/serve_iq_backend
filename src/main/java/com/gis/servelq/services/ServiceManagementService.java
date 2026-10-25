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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceManagementService {

    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;
    private final CounterServiceLinker counterServiceLinker;
    private final CodeUniquenessValidator codeUniquenessValidator;

    @Transactional
    public Services createService(ServiceRequest serviceRequest, AuthenticatedUser user) {
        codeUniquenessValidator.checkServiceCode(serviceRequest.getCode(), serviceRequest.getBranchId(), null);

        branchRepository.findById(serviceRequest.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch does not exist"));

        Services parent = null;
        if (serviceRequest.getParentId() != null) {
            parent = serviceRepository.findById(serviceRequest.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent service does not exist"));
        }

        Services newService = new Services();

        newService.setCode(CodeUniquenessValidator.normalize(serviceRequest.getCode()));
        newService.setName(serviceRequest.getName());
        newService.setArabicName(serviceRequest.getArabicName());
        newService.setDescription(blankToNull(serviceRequest.getDescription()));
        newService.setArabicDescription(blankToNull(serviceRequest.getArabicDescription()));
        newService.setParentId(serviceRequest.getParentId());
        newService.setEnabled(serviceRequest.getEnabled());
        newService.setSlaSeconds(serviceRequest.getSlaSeconds());
        newService.setBranchId(serviceRequest.getBranchId());

        newService = serviceRepository.save(newService);

        // Link counters once the service has an id, so both sides are updated
        if (serviceRequest.getCounterIds() != null && !serviceRequest.getCounterIds().isEmpty()) {
            counterServiceLinker.setServiceCounters(newService, serviceRequest.getCounterIds());
            newService = serviceRepository.save(newService);
        }

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

    // READ: Main (consumer/kiosk — enabled only)
    public List<ServiceResponseDTO> getMainServices(String branchId) {
        return serviceRepository.findMainServicesByBranchId(branchId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    // READ: Main (admin — includes disabled)
    public List<ServiceResponseDTO> getAllMainServices(String branchId) {
        return serviceRepository.findAllMainServicesByBranchId(branchId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    // READ: Sub (consumer/kiosk — enabled only)
    public List<ServiceResponseDTO> getSubServices(String parentId) {
        return serviceRepository.findByParentIdAndEnabledTrue(parentId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    // READ: Sub (admin — includes disabled)
    public List<ServiceResponseDTO> getAllSubServices(String parentId) {
        return serviceRepository.findByParentId(parentId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    // READ: ADMIN API
    public List<ServiceResponseDTO> getAllServices(String branchId) {
        return serviceRepository.findByBranchId(branchId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public Services updateService(String id, ServiceUpdateRequest updateRequest, AuthenticatedUser user) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        // Save old state for audit
        Services oldService = cloneService(service);

        // Prefix must stay unique in the branch the service ends up in, also
        // when only the branch changes.
        String targetBranchId = updateRequest.getBranchId() != null ? updateRequest.getBranchId() : service.getBranchId();
        boolean codeChanged = updateRequest.getCode() != null && !updateRequest.getCode().isBlank()
                && !CodeUniquenessValidator.normalize(updateRequest.getCode()).equals(CodeUniquenessValidator.normalize(service.getCode()));
        if (codeChanged || !targetBranchId.equals(service.getBranchId())) {
            String code = codeChanged ? updateRequest.getCode() : service.getCode();
            codeUniquenessValidator.checkServiceCode(code, targetBranchId, service.getId());
            service.setCode(CodeUniquenessValidator.normalize(code));
        }

        // Update only non-null fields
        if (updateRequest.getName() != null) service.setName(updateRequest.getName());
        if (updateRequest.getArabicName() != null) service.setArabicName(updateRequest.getArabicName());
        // An empty string clears a description; null leaves it unchanged
        if (updateRequest.getDescription() != null) service.setDescription(blankToNull(updateRequest.getDescription()));
        if (updateRequest.getArabicDescription() != null) {
            service.setArabicDescription(blankToNull(updateRequest.getArabicDescription()));
        }
        if (updateRequest.getParentId() != null) service.setParentId(updateRequest.getParentId());
        if (updateRequest.getCounterIds() != null) {
            counterServiceLinker.setServiceCounters(service, updateRequest.getCounterIds());
        }
        if (updateRequest.getEnabled() != null) service.setEnabled(updateRequest.getEnabled());
        if (updateRequest.getSlaSeconds() != null) {
            service.setSlaSeconds(updateRequest.getSlaSeconds() > 0 ? updateRequest.getSlaSeconds() : null);
        }
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
    @Transactional
    public void deleteService(String id, AuthenticatedUser user) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        counterServiceLinker.unlinkService(service);

        // If this service has a parent, remove it from the parent's children list
        if (service.getParentId() != null && !service.getParentId().isEmpty()) {
            serviceRepository.findById(service.getParentId()).ifPresent(parent -> {
                List<String> children = parent.getChildren();
                if (children != null && children.contains(service.getId())) {
                    children.remove(service.getId());
                    parent.setChildren(children);
                    serviceRepository.save(parent);
                }
            });
        }

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
        clone.setDescription(original.getDescription());
        clone.setArabicDescription(original.getArabicDescription());
        clone.setParentId(original.getParentId());
        clone.setEnabled(original.getEnabled());
        clone.setSlaSeconds(original.getSlaSeconds());
        clone.setCounterIds(original.getCounterIds());
        clone.setChildren(original.getChildren());
        clone.setBranchId(original.getBranchId());
        return clone;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
