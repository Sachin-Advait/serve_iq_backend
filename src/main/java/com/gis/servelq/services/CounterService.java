package com.gis.servelq.services;

import com.gis.servelq.dto.BranchResponse;
import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponse;
import com.gis.servelq.dto.ServiceResponseDTO;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.models.ServiceModel;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounterService {
    private final CounterRepository counterRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;


    // Convert Entity to Response DTO
    private CounterResponse convertToResponse(Counter counter) {
        CounterResponse response = new CounterResponse();
        response.setId(counter.getId());
        response.setCode(counter.getCode());
        response.setName(counter.getName());
        response.setEnabled(counter.getEnabled());
        response.setPaused(counter.getPaused());
        response.setUserId(counter.getUserId());
        response.setStatus(counter.getStatus());
        response.setCreatedAt(counter.getCreatedAt());
        response.setUpdatedAt(counter.getUpdatedAt());

        // Convert branch to BranchResponse
        if (counter.getBranch() != null) {
            // You might want to create a simplified BranchResponse or use existing one
            // For now, I'll assume you have a method to convert Branch to BranchResponse
            response.setBranch(convertBranchToResponse(counter.getBranch()));
        }

        if (counter.getUserId() != null) {
            userRepository.findById(counter.getUserId()).ifPresent(user ->
                    response.setUsername(user.getName())
            );
        }
        // Convert services to ServiceResponse list
        if (counter.getServices() != null) {
            response.setServices(counter.getServices().stream()
                    .map(this::convertServiceToResponse)
                    .collect(Collectors.toList()));
        }

        return response;
    }

    // Helper method to convert Branch to BranchResponse
    private BranchResponse convertBranchToResponse(Branch branch) {
        BranchResponse response = new BranchResponse();
        response.setId(branch.getId());
        response.setCode(branch.getCode());
        response.setName(branch.getName());
        response.setEnabled(branch.getEnabled());
        response.setCreatedAt(branch.getCreatedAt());
        response.setUpdatedAt(branch.getUpdatedAt());
        return response;
    }

    // Helper method to convert ServiceModel to ServiceResponse
    private ServiceResponseDTO convertServiceToResponse(ServiceModel service) {
        ServiceResponseDTO response = new ServiceResponseDTO();
        response.setId(service.getId());
        response.setCode(service.getCode());
        response.setName(service.getName());
        response.setEnabled(service.getEnabled());
        response.setCreatedAt(service.getCreatedAt());
        response.setUpdatedAt(service.getUpdatedAt());
        return response;
    }

    // Create a new counter
    @Transactional
    public CounterResponse createCounter(CounterRequest request) {
        // Check if counter code already exists in the same branch
        if (counterRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId()).isPresent()) {
            throw new RuntimeException("Counter with code " + request.getCode() + " already exists in this branch");
        }

        // Find branch
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        Counter counter = new Counter();
        counter.setCode(request.getCode());
        counter.setName(request.getName());
        counter.setBranch(branch);
        counter.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        counter.setPaused(request.getPaused() != null ? request.getPaused() : false);
        counter.setStatus(request.getStatus() != null ? request.getStatus() : CounterStatus.IDLE);

        // Add services if provided
        if (request.getServiceIds() != null && !request.getServiceIds().isEmpty()) {
            List<ServiceModel> services = serviceRepository.findAllById(request.getServiceIds());
            counter.setServices(services);
        }
        if (request.getUserId() != null) {
            counter.setUserId(request.getUserId());
        }

        Counter savedCounter = counterRepository.save(counter);
        return convertToResponse(savedCounter);
    }

    // Get all counters
    public List<CounterResponse> getAllCounters() {
        return counterRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Get counters by branch ID
    public List<CounterResponse> getCountersByBranch(String branchId) {
        return counterRepository.findByBranchId(branchId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Get counter by ID
    public CounterResponse getCounterById(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found with id: " + counterId));
        return convertToResponse(counter);
    }

    // Get counter by code and branch
    public CounterResponse getCounterByCodeAndBranch(String code, String branchId) {
        Counter counter = counterRepository.findByCodeAndBranchId(code, branchId)
                .orElseThrow(() -> new RuntimeException("Counter not found with code: " + code + " in branch: " + branchId));
        return convertToResponse(counter);
    }

    // Update counter
    @Transactional
    public CounterResponse updateCounter(String counterId, CounterRequest request) {
        Counter existingCounter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found with id: " + counterId));

        // Check if code is being changed and if it conflicts with other counters in the same branch
        if (!existingCounter.getCode().equals(request.getCode())) {
            String branchId = request.getBranchId() != null ? request.getBranchId() : existingCounter.getBranch().getId();
            if (counterRepository.findByCodeAndBranchId(request.getCode(), branchId).isPresent()) {
                throw new RuntimeException("Counter with code " + request.getCode() + " already exists in this branch");
            }
        }

        // Update basic fields
        if (request.getCode() != null) {
            existingCounter.setCode(request.getCode());
        }
        if (request.getUserId() != null) {
            existingCounter.setUserId(request.getUserId());
        }
        if (request.getName() != null) {
            existingCounter.setName(request.getName());
        }
        if (request.getEnabled() != null) {
            existingCounter.setEnabled(request.getEnabled());
        }
        if (request.getPaused() != null) {
            existingCounter.setPaused(request.getPaused());
        }
        if (request.getStatus() != null) {
            existingCounter.setStatus(request.getStatus());
        }

        // Update branch if provided
        if (request.getBranchId() != null && !existingCounter.getBranch().getId().equals(request.getBranchId())) {
            Branch newBranch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));
            existingCounter.setBranch(newBranch);
        }

        // Update services if provided
        if (request.getServiceIds() != null) {
            List<ServiceModel> services = serviceRepository.findAllById(request.getServiceIds());
            existingCounter.setServices(services);
        }

        Counter updatedCounter = counterRepository.save(existingCounter);
        return convertToResponse(updatedCounter);
    }

    // Delete counter
    @Transactional
    public void deleteCounter(String counterId) {
        if (!counterRepository.existsById(counterId)) {
            throw new RuntimeException("Counter not found with id: " + counterId);
        }
        counterRepository.deleteById(counterId);
    }

    // Enable/disable counter
    @Transactional
    public CounterResponse toggleCounter(String counterId, boolean enabled) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found with id: " + counterId));
        counter.setEnabled(enabled);
        Counter updatedCounter = counterRepository.save(counter);
        return convertToResponse(updatedCounter);
    }

    // Pause/resume counter
    @Transactional
    public CounterResponse togglePauseCounter(String counterId, boolean paused) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found with id: " + counterId));
        counter.setPaused(paused);
        Counter updatedCounter = counterRepository.save(counter);
        return convertToResponse(updatedCounter);
    }

    // Update counter status
    @Transactional
    public CounterResponse updateCounterStatus(String counterId, CounterStatus status) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found with id: " + counterId));
        counter.setStatus(status);
        Counter updatedCounter = counterRepository.save(counter);
        return convertToResponse(updatedCounter);
    }
}