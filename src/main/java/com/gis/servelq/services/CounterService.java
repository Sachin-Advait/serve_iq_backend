package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.*;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CounterService {

    private static final List<TokenStatus> ACTIVE_TOKEN_STATUSES =
            List.of(TokenStatus.SERVING, TokenStatus.CALLING, TokenStatus.REVIEW);
    private final CounterRepository counterRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    /**
     * Creates a Counter entity from request DTO
     * Handles null userId gracefully
     */
    private static Counter getCounter(CounterRequest request) {
        Counter counter = new Counter();
        counter.setCode(request.getCode().trim());
        counter.setName(request.getName().trim());
        counter.setBranchId(request.getBranchId());
        counter.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        counter.setPaused(request.getPaused() != null ? request.getPaused() : false);
        counter.setStatus(request.getStatus() != null ? request.getStatus() : CounterStatus.IDLE);

        // Only set serviceId if provided
        if (StringUtils.hasText(request.getServiceId())) {
            counter.setServiceId(request.getServiceId());
        }

        // Only set userId if provided
        if (StringUtils.hasText(request.getUserId())) {
            counter.setUserId(request.getUserId());
        }

        return counter;
    }

    /**
     * Calculate average service time in minutes for a counter
     */
    public Double getAverageServiceTimeMinutesByCounter(String counterId) {
        try {
            Double avgSeconds = tokenRepository.getAvgServiceTimeSecondsByCounter(counterId);
            if (avgSeconds == null || avgSeconds <= 0) {
                return 0.0;
            }
            return Math.round((avgSeconds / 60.0) * 100.0) / 100.0; // Round to 2 decimal places
        } catch (Exception e) {
            log.error("Error calculating average service time for counter: {}", counterId, e);
            return 0.0;
        }
    }

    /**
     * Convert Counter entity to Response DTO
     * Handles null userId and serviceId gracefully
     * Enriches response with service name and code
     */
    private CounterResponseDTO convertToResponse(Counter counter) {
        // Validate branch exists
        branchRepository.findById(counter.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + counter.getBranchId()));

        // Get username if userId exists
        String username = null;
        if (StringUtils.hasText(counter.getUserId())) {
            username = userRepository.findById(counter.getUserId())
                    .map(User::getName)
                    .orElse(null);
        }

        Double avgSeconds = getAverageServiceTimeMinutesByCounter(counter.getId());
        CounterResponseDTO response = CounterResponseDTO.fromEntity(counter, username, avgSeconds);

        // Enrich with service information
        if (StringUtils.hasText(counter.getServiceId())) {
            serviceRepository.findById(counter.getServiceId()).ifPresent(service -> {
                response.setServiceName(service.getName());
                response.setServiceCode(service.getCode());
            });
        }

        return response;
    }

    /**
     * Create a new counter
     * Production-ready with proper validation and null handling
     */
    @Transactional
    public CounterResponseDTO createCounter(CounterRequest request, AuthenticatedUser currentUser) {
        // Validate required fields
        validateCounterRequest(request);

        // Check for duplicate counter code in the same branch
        counterRepository.findByCodeAndBranchId(request.getCode().trim(), request.getBranchId())
                .ifPresent(counter -> {
                    throw new BusinessException("Counter with code " + request.getCode() + " already exists in this branch");
                });

        // Validate branch exists
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + request.getBranchId()));

        // Create and save counter
        Counter counter = getCounter(request);
        Counter saved = counterRepository.save(counter);

        // Assign user to counter if userId is provided
        if (StringUtils.hasText(request.getUserId())) {
            assignUserToCounter(request.getUserId(), saved.getId());
        }

        // Audit log
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

        log.info("Counter created successfully: {} (ID: {})", saved.getName(), saved.getId());
        return convertToResponse(saved);
    }

    /**
     * Get all counters
     */
    public List<CounterResponseDTO> getAllCounters() {
        return counterRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get counters by branch
     */
    public List<CounterResponseDTO> getCountersByBranch(String branchId) {
        return counterRepository.findByBranchIdOrderByCreatedAtAsc(branchId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get counter by ID
     */
    public CounterResponseDTO getCounterById(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));
        return convertToResponse(counter);
    }

    /**
     * Update counter with production-ready validation
     */
    @Transactional
    public CounterResponseDTO updateCounter(String counterId, CounterUpdateRequest counterRequest,
                                            AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

        // Clone the old counter state for audit
        Counter oldCounter = cloneCounter(counter);

        // Check for duplicate code if code is being changed
        if (StringUtils.hasText(counterRequest.getCode()) &&
                !counterRequest.getCode().equals(counter.getCode()) &&
                counterRepository.findByCodeAndBranchId(counterRequest.getCode().trim(), counter.getBranchId()).isPresent()) {
            throw new BusinessException("Counter code already exists in this branch");
        }

        // Update fields if provided
        if (StringUtils.hasText(counterRequest.getCode())) {
            counter.setCode(counterRequest.getCode().trim());
        }
        if (StringUtils.hasText(counterRequest.getName())) {
            counter.setName(counterRequest.getName().trim());
        }
        if (counterRequest.getEnabled() != null) {
            counter.setEnabled(counterRequest.getEnabled());
        }
        if (counterRequest.getPaused() != null) {
            counter.setPaused(counterRequest.getPaused());
        }
        if (counterRequest.getStatus() != null) {
            counter.setStatus(counterRequest.getStatus());
        }

        // Handle branch change
        if (StringUtils.hasText(counterRequest.getBranchId()) &&
                !counterRequest.getBranchId().equals(counter.getBranchId())) {
            branchRepository.findById(counterRequest.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + counterRequest.getBranchId()));
            counter.setBranchId(counterRequest.getBranchId());
        }

        // Handle service change
        if (counterRequest.getServiceId() != null) {
            if (StringUtils.hasText(counterRequest.getServiceId())) {
                serviceRepository.findById(counterRequest.getServiceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + counterRequest.getServiceId()));
                counter.setServiceId(counterRequest.getServiceId());
            } else {
                counter.setServiceId(null);
            }
        }

        // Handle user assignment change
        if (counterRequest.getUserId() != null) {
            if (StringUtils.hasText(counterRequest.getUserId())) {
                // Assign new user
                if (!Objects.equals(counter.getUserId(), counterRequest.getUserId())) {
                    assignUserToCounter(counterRequest.getUserId(), counterId);
                    counter.setUserId(counterRequest.getUserId());
                }
            } else {
                // Unassign user
                unassignUserFromCounter(counter.getUserId());
                counter.setUserId(null);
            }
        }

        Counter updated = counterRepository.save(counter);

        // Audit log with changes
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
                this.request
        );

        // Publish event for counter status change
        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_STATUS_CHANGED,
                updated.getBranchId(),
                null,
                null,
                counterId,
                Instant.now()
        ));

        log.info("Counter updated successfully: {} (ID: {})", updated.getName(), updated.getId());
        return convertToResponse(updated);
    }

    /**
     * Enable/disable counter
     */
    @Transactional
    public CounterResponseDTO toggleCounter(String counterId, boolean enabled, AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

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

    /**
     * Pause/resume counter
     */
    @Transactional
    public CounterResponseDTO togglePauseCounter(String counterId, boolean paused, AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

        counter.setPaused(paused);

        // Update status based on pause state
        if (paused) {
            counter.setStatus(CounterStatus.PAUSED);
        } else {
            counter.setStatus(CounterStatus.IDLE);
        }

        Counter saved = counterRepository.save(counter);

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

    /**
     * Delete counter
     */
    @Transactional
    public void deleteCounter(String counterId, AuthenticatedUser user) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

        // Audit log before deletion
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

        // Unassign user from counter if exists
        if (StringUtils.hasText(counter.getUserId())) {
            unassignUserFromCounter(counter.getUserId());
        }

        counterRepository.deleteById(counterId);
        log.info("Counter deleted successfully: {} (ID: {})", counter.getName(), counterId);
    }

    /**
     * Get counter status details with token info
     */
    @Transactional(readOnly = true)
    public CounterStatusResponseDTO getCounterStatusDetails(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

        CounterStatusResponseDTO response = new CounterStatusResponseDTO();
        response.setCounterId(counter.getId());
        response.setCode(counter.getCode());
        response.setName(counter.getName());
        response.setEnabled(counter.getEnabled());
        response.setPaused(counter.getPaused());
        response.setStatus(counter.getStatus());

        // Fetch currently serving token if counter is in active serving state
        if (counter.getStatus() == CounterStatus.SERVING ||
                counter.getStatus() == CounterStatus.CALLING ||
                counter.getStatus() == CounterStatus.COMPLETE) {

            tokenRepository.findByStatusInAndAssignedCounterId(
                    List.of(TokenStatus.SERVING, TokenStatus.CALLING, TokenStatus.REVIEW),
                    counterId
            ).ifPresentOrElse(token -> {
                response.setTokenId(token.getId());
                response.setTokenNumber(token.getToken());
                response.setServiceId(token.getServiceId());

                if (StringUtils.hasText(token.getServiceId())) {
                    serviceRepository.findById(token.getServiceId())
                            .ifPresent(service -> {
                                response.setServiceName(service.getName());
                                response.setServiceCode(service.getCode());
                            });
                }
            }, response::clearTokenDetails);
        }

        return response;
    }

    /**
     * Helper method to assign user to counter
     * Handles reassignment from other counters
     */
    private void assignUserToCounter(String userId, String counterId) {
        User assignedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // If user is already assigned to another counter, unassign them
        if (StringUtils.hasText(assignedUser.getCounterId()) &&
                !assignedUser.getCounterId().equals(counterId)) {
            counterRepository.findById(assignedUser.getCounterId())
                    .ifPresent(oldCounter -> {
                        oldCounter.setUserId(null);
                        counterRepository.save(oldCounter);
                    });
        }

        assignedUser.setCounterId(counterId);
        userRepository.save(assignedUser);
    }

    /**
     * Helper method to unassign user from counter
     */
    private void unassignUserFromCounter(String userId) {
        if (StringUtils.hasText(userId)) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setCounterId(null);
                userRepository.save(user);
            });
        }
    }

    /**
     * Helper method to validate counter request
     */
    private void validateCounterRequest(CounterRequest request) {
        if (!StringUtils.hasText(request.getCode())) {
            throw new BusinessException("Counter code is required");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException("Counter name is required");
        }
        if (!StringUtils.hasText(request.getBranchId())) {
            throw new BusinessException("Branch ID is required");
        }
    }

    /**
     * TV/display-board feed: every counter in the branch with its current
     * token number, serving service, and status — built with two bulk
     * queries (services, active tokens) instead of one query per counter.
     */
    @Transactional(readOnly = true)
    public List<CounterDisplayDTO> getCounterDisplayBoard(String branchId) {
        List<Counter> counters = counterRepository.findByBranchIdOrderByCreatedAtAsc(branchId);
        if (counters.isEmpty()) {
            return List.of();
        }

        List<String> counterIds = counters.stream()
                .map(Counter::getId)
                .collect(Collectors.toList());

        List<String> serviceIds = counters.stream()
                .map(Counter::getServiceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, String> serviceNameById = serviceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(Services::getId, Services::getName));

        Map<String, Token> activeTokenByCounter = tokenRepository
                .findByStatusInAndAssignedCounterIdIn(ACTIVE_TOKEN_STATUSES, counterIds)
                .stream()
                .collect(Collectors.toMap(
                        Token::getAssignedCounterId,
                        t -> t,
                        (first, second) -> first
                ));

        return counters.stream().map(counter -> {
            CounterDisplayDTO dto = new CounterDisplayDTO();
            dto.setCounterId(counter.getId());
            dto.setCounterCode(counter.getCode());
            dto.setCounterName(counter.getName());
            dto.setEnabled(counter.getEnabled());
            dto.setPaused(counter.getPaused());
            dto.setCounterStatus(counter.getStatus());

            Token activeToken = activeTokenByCounter.get(counter.getId());
            if (activeToken != null) {
                dto.setTokenId(activeToken.getId());
                dto.setTokenNumber(activeToken.getToken());
                dto.setCalledAt(activeToken.getStartAt());
                dto.setServiceId(activeToken.getServiceId());
                dto.setServiceName(serviceNameById.get(activeToken.getServiceId()));
            } else {
                dto.setServiceId(counter.getServiceId());
                dto.setServiceName(serviceNameById.get(counter.getServiceId()));
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Helper method to clone counter for audit comparison
     */
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