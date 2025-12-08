package com.gis.servelq.services;

import com.gis.servelq.dto.ServiceRequest;
import com.gis.servelq.dto.ServiceResponseDTO;
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
        copyRequestToEntity(newService, request);

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

    // UPDATE
    public Services updateService(String id, ServiceRequest request) {

        Services service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        if (!service.getCode().equals(request.getCode())) {
            serviceRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId())
                    .ifPresent(s -> {
                        throw new RuntimeException("Service code already exists");
                    });
        }

        copyRequestToEntity(service, request);

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


    // Helper method
    private void copyRequestToEntity(Services entity, ServiceRequest req) {
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setArabicName(req.getArabicName());
        entity.setParentId(req.getParentId());
        entity.setEnabled(req.getEnabled());
        entity.setBranchId(req.getBranchId());
        entity.setCounterIds(req.getCounterIds());
    }
}
