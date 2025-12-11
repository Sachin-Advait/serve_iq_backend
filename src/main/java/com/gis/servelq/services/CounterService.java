package com.gis.servelq.services;

import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponseDTO;
import com.gis.servelq.dto.CounterStatusResponseDTO;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
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
    private final TokenRepository tokenRepository;
    private final SocketService socketService;

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

    private CounterResponseDTO convertToResponse(Counter counter) {
        Branch branch = branchRepository.findById(counter.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        String username = null;
        if (counter.getUserId() != null) {
            username = userRepository.findById(counter.getUserId())
                    .map(User::getName)
                    .orElse(null);
        }
        Services service = null;
        if (counter.getServiceId() != null) {
            service = serviceRepository.findById(counter.getServiceId()).orElse(null);
        }

        return CounterResponseDTO.fromEntity(counter, branch, username, service);
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

    // Update counter (now includes status update logic also)
    @Transactional
    public CounterResponseDTO updateCounter(String counterId, CounterRequest request) {

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));

        if (request.getCode() != null &&
                !request.getCode().equals(counter.getCode()) &&
                counterRepository.findByCodeAndBranchId(request.getCode(), counter.getBranchId()).isPresent()) {
            throw new RuntimeException("Counter code already exists in this branch");
        }
        if (request.getCode() != null) counter.setCode(request.getCode());
        if (request.getName() != null) counter.setName(request.getName());
        if (request.getEnabled() != null) counter.setEnabled(request.getEnabled());
        if (request.getPaused() != null) counter.setPaused(request.getPaused());
        if (request.getStatus() != null) counter.setStatus(request.getStatus());
        if (request.getUserId() != null) counter.setUserId(request.getUserId());

        if (request.getBranchId() != null && !request.getBranchId().equals(counter.getBranchId())) {
            branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            counter.setBranchId(request.getBranchId());
        }
        if (request.getServiceId() != null) counter.setServiceId(request.getServiceId());

        Counter updated = counterRepository.save(counter);
        notifyCounterDisplay(counterId);

        return convertToResponse(updated);
    }

    // Enable/disable counter
    @Transactional
    public CounterResponseDTO toggleCounter(String counterId, boolean enabled) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setEnabled(enabled);
        Counter saved = counterRepository.save(counter);

        notifyCounterDisplay(counterId);
        return convertToResponse(saved);
    }

    // Pause/resume counter
    @Transactional
    public CounterResponseDTO togglePauseCounter(String counterId, boolean paused) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setPaused(paused);
        Counter saved = counterRepository.save(counter);

        notifyCounterDisplay(counterId);
        return convertToResponse(saved);
    }

    // Delete counter
    @Transactional
    public void deleteCounter(String counterId) {
        if (!counterRepository.existsById(counterId)) {
            throw new RuntimeException("Counter not found");
        }
        counterRepository.deleteById(counterId);
    }

    @Transactional(readOnly = true)
    public CounterStatusResponseDTO getCounterStatusDetails(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));

        CounterStatusResponseDTO response = new CounterStatusResponseDTO();
        response.setCounterId(counter.getId());
        response.setCode(counter.getCode());
        response.setName(counter.getName());
        response.setEnabled(counter.getEnabled());
        response.setPaused(counter.getPaused());
        response.setStatus(counter.getStatus().name());

        // Fetch currently serving token
        if (counter.getStatus() == CounterStatus.SERVING || counter.getStatus() == CounterStatus.CALLING) {
            var token = tokenRepository
                    .findByStatusInAndAssignedCounterId(
                            List.of(TokenStatus.SERVING, TokenStatus.CALLING),
                            counterId
                    )
                    .orElse(null);

            if (token != null) {
                response.setTokenId(token.getId());
                response.setTokenNumber(token.getToken());
                response.setServiceId(token.getServiceId());
                serviceRepository.findById(token.getServiceId()).ifPresent(service ->
                        response.setServiceName(service.getName())
                );
            } else {
                response.clearTokenDetails();
            }
        }

        return response;
    }

    public void notifyCounterDisplay(String counterId) {
        CounterStatusResponseDTO data = getCounterStatusDetails(counterId);
        socketService.broadcast("/topic/counter/" + counterId, data);
    }
}
