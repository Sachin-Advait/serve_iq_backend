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

    public ServiceModel createService(ServiceRequest request) {
        // Check if service code already exists in this branch
        serviceRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId())
                .ifPresent(s -> {
                    throw new RuntimeException("Service code already exists in this branch");
                });

        if (request.getParentId() != null) {
            serviceRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent service does not exist"));
        }

        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch does not exist"));


        ServiceModel serviceModel = new ServiceModel();
        serviceModel.setCode(request.getCode());
        serviceModel.setName(request.getName());
        serviceModel.setArabicName(request.getArabicName());
        serviceModel.setParentId(request.getParentId());
        serviceModel.setSlaSec(request.getSlaSec());
        serviceModel.setEnabled(request.getEnabled());
        serviceModel.setBranchId(request.getBranchId());

        return serviceRepository.save(serviceModel);
    }

    public List<ServiceResponseDTO> getMainServices(String branchId) {
        List<ServiceModel> services = serviceRepository.findMainServicesByBranchId(branchId);
        return services.stream()
                .map(ServiceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ServiceResponseDTO> getSubServices(String parentId) {
        List<ServiceModel> services = serviceRepository.findByParentIdAndEnabledTrue(parentId);
        return services.stream()
                .map(ServiceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }


    public List<ServiceResponseDTO> getAllServices(String branchId) {
        List<ServiceModel> services = serviceRepository.findByBranchIdAndEnabledTrue(branchId);
        return services.stream()
                .map(ServiceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
