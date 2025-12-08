package com.gis.servelq.services;

import com.gis.servelq.dto.BranchResponseDTO;
import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponseDTO;
import com.gis.servelq.dto.ServiceResponseDTO;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.models.Services;
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

    private static Counter getCounter(CounterRequest request) {
        Counter counter = new Counter();
        counter.setCode(request.getCode());
        counter.setName(request.getName());
        counter.setBranchId(request.getBranchId());
        counter.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        counter.setPaused(request.getPaused() != null ? request.getPaused() : false);
        counter.setStatus(request.getStatus());

        // Assign services as List<String>
        counter.setServiceId(request.getServiceId());

        // Assign user if provided
        if (request.getUserId() != null) {
            counter.setUserId(request.getUserId());
        }
        return counter;
    }

    // Convert Entity to Response DTO
    private CounterResponseDTO convertToResponse(Counter counter) {
        Branch branch = branchRepository.findById(counter.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + counter.getBranchId()));

        CounterResponseDTO response = new CounterResponseDTO();

        response.setId(counter.getId());
        response.setCode(counter.getCode());
        response.setName(counter.getName());
        response.setEnabled(counter.getEnabled());
        response.setPaused(counter.getPaused());
        response.setUserId(counter.getUserId());
        response.setStatus(counter.getStatus());
        response.setCreatedAt(counter.getCreatedAt());
        response.setUpdatedAt(counter.getUpdatedAt());

        // Set branch
        response.setBranch(convertBranchToResponse(branch));

        // Set username from user table (optional)
        userRepository.findById(counter.getUserId()).ifPresent(user ->
                response.setUsername(user.getName())
        );

        if (counter.getServiceId() != null) {
            serviceRepository.findById(counter.getServiceId()).ifPresent(service ->
                    response.setService(convertServiceToResponse(service))
            );
        }

        return response;
    }

    private BranchResponseDTO convertBranchToResponse(Branch branch) {
        BranchResponseDTO response = new BranchResponseDTO();
        response.setId(branch.getId());
        response.setCode(branch.getCode());
        response.setName(branch.getName());
        response.setEnabled(branch.getEnabled());
        return response;
    }

    private ServiceResponseDTO convertServiceToResponse(Services service) {
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
    public CounterResponseDTO createCounter(CounterRequest request) {
        if (counterRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId()).isPresent()) {
            throw new RuntimeException("Counter with code " + request.getCode() + " already exists in this branch");
        }

        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        Counter counter = getCounter(request);

        Counter saved = counterRepository.save(counter);
        return convertToResponse(saved);
    }

    // Get all counters
    public List<CounterResponseDTO> getAllCounters() {
        return counterRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Get counters by branch
    public List<CounterResponseDTO> getCountersByBranch(String branchId) {
        return counterRepository.findByBranchId(branchId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Get counter by ID
    public CounterResponseDTO getCounterById(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        return convertToResponse(counter);
    }

    // Get counter by code+branch
    public CounterResponseDTO getCounterByCodeAndBranch(String code, String branchId) {
        Counter counter = counterRepository.findByCodeAndBranchId(code, branchId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        return convertToResponse(counter);
    }

    // Update counter
    @Transactional
    public CounterResponseDTO updateCounter(String counterId, CounterRequest request) {

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));

        // Prevent duplicate code
        if (request.getCode() != null &&
                !request.getCode().equals(counter.getCode()) &&
                counterRepository.findByCodeAndBranchId(request.getCode(), counter.getBranchId()).isPresent()) {
            throw new RuntimeException("Counter code already exists in branch");
        }

        if (request.getCode() != null) counter.setCode(request.getCode());
        if (request.getName() != null) counter.setName(request.getName());
        if (request.getEnabled() != null) counter.setEnabled(request.getEnabled());
        if (request.getPaused() != null) counter.setPaused(request.getPaused());
        if (request.getStatus() != null) counter.setStatus(request.getStatus());
        if (request.getUserId() != null) counter.setUserId(request.getUserId());

        // Update branch if changed
        if (request.getBranchId() != null && !request.getBranchId().equals(counter.getBranchId())) {
            branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            counter.setBranchId(request.getBranchId());
        }
        if (request.getServiceId() != null) counter.setServiceId(request.getServiceId());

        Counter updated = counterRepository.save(counter);
        return convertToResponse(updated);
    }

    // Delete counter
    @Transactional
    public void deleteCounter(String counterId) {
        if (!counterRepository.existsById(counterId)) {
            throw new RuntimeException("Counter not found");
        }
        counterRepository.deleteById(counterId);
    }

    // Enable/disable counter
    @Transactional
    public CounterResponseDTO toggleCounter(String counterId, boolean enabled) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setEnabled(enabled);
        return convertToResponse(counterRepository.save(counter));
    }

    // Pause/resume counter
    @Transactional
    public CounterResponseDTO togglePauseCounter(String counterId, boolean paused) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setPaused(paused);
        return convertToResponse(counterRepository.save(counter));
    }

    // Update status manually
    @Transactional
    public CounterResponseDTO updateCounterStatus(String counterId, CounterStatus status) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setStatus(status);
        return convertToResponse(counterRepository.save(counter));
    }
}
