package com.gis.servelq.services;

import com.gis.servelq.dto.ServiceRequest;
import com.gis.servelq.dto.ServiceResponseDTO;
import com.gis.servelq.dto.ServiceUpdateRequest;
import com.gis.servelq.models.Services;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceManagementService {

    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;

    public Services createService(ServiceRequest request) {
        serviceRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId())
                .ifPresent(s -> {
                    throw new RuntimeException("Service code already exists");
                });

        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch does not exist"));

        Services parent = null;
        if (request.getParentId() != null) {
            parent = serviceRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent service does not exist"));
        }

        Services newService = new Services();

        newService.setCode(request.getCode());
        newService.setName(request.getName());
        newService.setArabicName(request.getArabicName());
        newService.setParentId(request.getParentId());
        newService.setEnabled(request.getEnabled());
        newService.setBranchId(request.getBranchId());
        newService.setCounterIds(request.getCounterIds());

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

    // READ: All
    public List<ServiceResponseDTO> getAllServices(String branchId) {
        return serviceRepository.findByBranchIdAndEnabledTrue(branchId)
                .stream().map(ServiceResponseDTO::fromEntity).collect(Collectors.toList());
    }

    public Services updateService(String id, ServiceUpdateRequest request) {

        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        // Null-safe code uniqueness check
        if (request.getCode() != null &&
                !request.getCode().equals(service.getCode())) {

            serviceRepository.findByCodeAndBranchId(
                    request.getCode(),
                    request.getBranchId()
            ).ifPresent(s -> {
                throw new RuntimeException("Service code already exists");
            });

            service.setCode(request.getCode());
        }

        // Update only non-null fields
        if (request.getName() != null) service.setName(request.getName());
        if (request.getArabicName() != null) service.setArabicName(request.getArabicName());
        if (request.getParentId() != null) service.setParentId(request.getParentId());
        if (request.getCounterIds() != null) service.setCounterIds(request.getCounterIds());
        if (request.getEnabled() != null) service.setEnabled(request.getEnabled());
        if (request.getBranchId() != null) service.setBranchId(request.getBranchId());

        return serviceRepository.save(service);
    }

    // SOFT DELETE
    public void disableService(String id) {
        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        service.setEnabled(false);
        serviceRepository.save(service);
    }

    // HARD DELETE
    public void deleteService(String id) {
        if (!serviceRepository.existsById(id)) {
            throw new RuntimeException("Service not found");
        }
        serviceRepository.deleteById(id);
    }
}
