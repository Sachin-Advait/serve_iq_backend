package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.CounterRequest;
import com.gis.servelq.dto.CounterResponseDTO;
import com.gis.servelq.dto.CounterStatusResponseDTO;
import com.gis.servelq.dto.CounterUpdateRequest;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounterService {

    private final CounterRepository counterRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    private static Counter getCounter(CounterRequest request) {
        Counter counter = new Counter();
        counter.setCode(request.getCode());
        counter.setName(request.getName());
        counter.setBranchId(request.getBranchId());
        counter.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        counter.setPaused(request.getPaused() != null ? request.getPaused() : false);
        counter.setStatus(request.getStatus());
        counter.setServiceId(request.getServiceId());

        if (request.getUserId() != null) {
            counter.setUserId(request.getUserId());
        }
        return counter;
    }

    public Double getAverageServiceTimeMinutesByCounter(String counterId) {
        Double avgSeconds =
                tokenRepository.getAvgServiceTimeSecondsByCounter(counterId);

        if (avgSeconds == null) return 0.0;

        return avgSeconds / 60.0;
    }

    private CounterResponseDTO convertToResponse(Counter counter) {
        branchRepository.findById(counter.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        serviceRepository.findById(counter.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found"));

        String username = userRepository.findById(counter.getUserId())
                .map(User::getName)
                .orElse(null);

        Double avgSeconds = getAverageServiceTimeMinutesByCounter(counter.getId());
        return CounterResponseDTO.fromEntity(counter, username, avgSeconds);
    }

    // Create a new counter
    @Transactional
    public CounterResponseDTO createCounter(CounterRequest request, AuthenticatedUser currentUser) {
        if (counterRepository.findByCodeAndBranchId(request.getCode(), request.getBranchId()).isPresent()) {
            throw new RuntimeException("Counter with code " + request.getCode() + " already exists in this branch");
        }

        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));

        Counter counter = getCounter(request);
        Counter saved = counterRepository.save(counter);

        // Fixed: Renamed 'user' to 'assignedUser' to avoid conflict
        User assignedUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        assignedUser.setCounterId(saved.getId());
        userRepository.save(assignedUser);

        auditLogService.log(
                AuditAction.COUNTER_CREATED,
                "Counter",
                saved.getId(),
                saved.getName(),
                "Counter created: " + saved.getName() + " in branch " + saved.getBranchId(),
                currentUser,
                saved.getBranchId(),
                this.request
        );
        return convertToResponse(saved);
    }

    // Get all counters
    public List<CounterResponseDTO> getAllCounters() {
        return counterRepository.findAll().stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    // Get counters by branch
    public List<CounterResponseDTO> getCountersByBranch(String branchId) {
        return counterRepository.findByBranchIdOrderByCreatedAtAsc(branchId).stream().map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Get counter by ID
    public CounterResponseDTO getCounterById(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        return convertToResponse(counter);
    }

    // Update counter (now includes status update logic also)
    // Update counter (now includes status update logic also)
    @Transactional
    public CounterResponseDTO updateCounter(String counterId, CounterUpdateRequest counterRequest,
                                            AuthenticatedUser user) {

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        // Clone the old counter state for audit
        Counter oldCounter = cloneCounter(counter);

        if (counterRequest.getCode() != null &&
                !counterRequest.getCode().equals(counter.getCode()) &&
                counterRepository.findByCodeAndBranchId(counterRequest.getCode(), counter.getBranchId()).isPresent()) {
            throw new BusinessException("Counter code already exists in this branch");
        }
        if (counterRequest.getCode() != null) counter.setCode(counterRequest.getCode());
        if (counterRequest.getName() != null) counter.setName(counterRequest.getName());
        if (counterRequest.getEnabled() != null) counter.setEnabled(counterRequest.getEnabled());
        if (counterRequest.getPaused() != null) counter.setPaused(counterRequest.getPaused());
        if (counterRequest.getStatus() != null) counter.setStatus(counterRequest.getStatus());

        if (counterRequest.getBranchId() != null && !counterRequest.getBranchId().equals(counter.getBranchId())) {
            branchRepository.findById(counterRequest.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            counter.setBranchId(counterRequest.getBranchId());
        }
        if (counterRequest.getServiceId() != null) counter.setServiceId(counterRequest.getServiceId());
        if (counterRequest.getUserId() != null && !Objects.equals(counter.getUserId(), counterRequest.getUserId())) {
            counter.setUserId(counterRequest.getUserId());

            User assignedUser = userRepository.findById(counterRequest.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            assignedUser.setCounterId(counterId);
            userRepository.save(assignedUser);
        }

        Counter updated = counterRepository.save(counter);

        auditLogService.logWithChanges(
                AuditAction.COUNTER_UPDATED,
                "Counter",
                updated.getId(),
                updated.getName(),
                "Counter updated: " + updated.getName(),
                oldCounter,
                updated,
                user,
                updated.getBranchId(),
                this.request  // Fixed: Use this.request to access HttpServletRequest
        );

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_STATUS_CHANGED,
                updated.getBranchId(),
                null,
                null,
                counterId,
                Instant.now()
        ));

        return convertToResponse(updated);
    }

    // Enable/disable counter
    @Transactional
    public CounterResponseDTO toggleCounter(String counterId, boolean enabled, AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setEnabled(enabled);
        Counter saved = counterRepository.save(counter);

        auditLogService.log(
                AuditAction.COUNTER_STATUS_CHANGED,
                "Counter",
                saved.getId(),
                saved.getName(),
                "Counter " + (enabled ? "enabled" : "disabled") + ": " + saved.getName(),
                user,
                saved.getBranchId(),
                request
        );

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_STATUS_CHANGED,
                saved.getBranchId(),
                null,
                null,
                counterId,
                Instant.now()
        ));

        return convertToResponse(saved);
    }

    // Pause/resume counter
    @Transactional
    public CounterResponseDTO togglePauseCounter(String counterId, boolean paused, AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));
        counter.setPaused(paused);
        Counter saved = counterRepository.save(counter);

        // Fixed: Added audit logging
        auditLogService.log(
                AuditAction.COUNTER_STATUS_CHANGED,
                "Counter",
                saved.getId(),
                saved.getName(),
                "Counter " + (paused ? "paused" : "resumed") + ": " + saved.getName(),
                user,
                saved.getBranchId(),
                request
        );

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_STATUS_CHANGED,
                saved.getBranchId(),
                null,
                null,
                counterId,
                Instant.now()
        ));
        return convertToResponse(saved);
    }

    // Delete counter
    @Transactional
    public void deleteCounter(String counterId, AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));

        // Fixed: Added audit logging before deletion
        auditLogService.log(
                AuditAction.COUNTER_DELETED,
                "Counter",
                counter.getId(),
                counter.getName(),
                "Counter deleted: " + counter.getName(),
                user,
                counter.getBranchId(),
                request
        );

        if (counter.getUserId() != null && !counter.getUserId().isBlank()) {
            userRepository.findById(counter.getUserId()).ifPresent(u -> u.setCounterId(null));
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
        response.setStatus(counter.getStatus());

        // Fetch currently serving token
        if (counter.getStatus() == CounterStatus.SERVING || counter.getStatus() == CounterStatus.CALLING ||
                counter.getStatus() == CounterStatus.COMPLETE) {
            var token = tokenRepository.findByStatusInAndAssignedCounterId(
                    List.of(TokenStatus.SERVING, TokenStatus.CALLING, TokenStatus.REVIEW),
                    counterId
            ).orElse(null);

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

    // Helper method to clone counter for audit comparison
    private Counter cloneCounter(Counter original) {
        Counter clone = new Counter();
        clone.setId(original.getId());
        clone.setCode(original.getCode());
        clone.setName(original.getName());
        clone.setEnabled(original.getEnabled());
        clone.setPaused(original.getPaused());
        clone.setBranchId(original.getBranchId());
        clone.setUserId(original.getUserId());
        clone.setServiceId(original.getServiceId());
        clone.setStatus(original.getStatus());
        return clone;
    }
}