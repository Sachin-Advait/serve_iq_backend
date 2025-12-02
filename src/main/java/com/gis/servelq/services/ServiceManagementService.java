package com.gis.servelq.services;

import com.gis.servelq.dto.ServiceRequest;
import com.gis.servelq.dto.ServiceResponseDTO;
import com.gis.servelq.models.ServiceModel;
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

    // CREATE
    public ServiceModel createService(ServiceRequest request) {

        serviceRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId())
                .ifPresent(s -> { throw new RuntimeException("Service code already exists"); });

        if (request.getParentId() != null) {
            serviceRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent service does not exist"));
        }

        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch does not exist"));

        ServiceModel serviceModel = new ServiceModel();
        copyRequestToEntity(serviceModel, request);

        return serviceRepository.save(serviceModel);
    }

    // READ: Get by ID
    public ServiceResponseDTO getServiceById(String id) {
        ServiceModel service = serviceRepository.findById(id)
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
    public ServiceModel updateService(String id, ServiceRequest request) {

        ServiceModel service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        if (!service.getCode().equals(request.getCode())) {
            serviceRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId())
                    .ifPresent(s -> { throw new RuntimeException("Service code already exists"); });
        }

        copyRequestToEntity(service, request);

        return serviceRepository.save(service);
    }

    // SOFT DELETE
    public void disableService(String id) {
        ServiceModel service = serviceRepository.findById(id)
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
    private void copyRequestToEntity(ServiceModel entity, ServiceRequest req) {
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setArabicName(req.getArabicName());
        entity.setParentId(req.getParentId());
        entity.setSlaSec(req.getSlaSec());
        entity.setEnabled(req.getEnabled());
        entity.setBranchId(req.getBranchId());
    }
}
