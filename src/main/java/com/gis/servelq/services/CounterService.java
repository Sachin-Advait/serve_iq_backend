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
import com.gis.servelq.security.TokenRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class CounterService {

    private static final List<TokenStatus> ACTIVE_TOKEN_STATUSES =
            List.of(TokenStatus.SERVING, TokenStatus.CALLING, TokenStatus.REVIEW);
    private static final List<TokenStatus> IN_PROGRESS = List.of(TokenStatus.CALLING, TokenStatus.SERVING);
    private final CounterRepository counterRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;
    private final CounterServiceLinker counterServiceLinker;
    private final TokenRevocationService tokenRevocationService;

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

        // Enrich with service information; serviceName/serviceCode describe the first service
        List<String> serviceIds = CounterServiceLinker.serviceIdsOf(counter);
        if (!serviceIds.isEmpty()) {
            Map<String, Services> servicesById = serviceRepository.findAllById(serviceIds).stream()
                    .collect(Collectors.toMap(Services::getId, s -> s));
            List<Services> services = serviceIds.stream()
                    .map(servicesById::get)
                    .filter(Objects::nonNull)
                    .toList();
            response.setServiceId(serviceIds.get(0));
            response.setServiceIds(services.stream().map(Services::getId).toList());
            response.setServiceNames(services.stream().map(Services::getName).toList());
            if (!services.isEmpty()) {
                response.setServiceName(services.get(0).getName());
                response.setServiceCode(services.get(0).getCode());
            }
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

        List<String> serviceIds = requestedServiceIds(request.getServiceIds(), request.getServiceId());
        if (serviceIds != null && !serviceIds.isEmpty()) {
            counterServiceLinker.setCounterServices(saved, serviceIds);
            saved = counterRepository.save(saved);
        }

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

        // Handle service change. serviceIds replaces the whole list; a lone
        // serviceId from an older client that matches the current first
        // service is left alone so it doesn't wipe the counter's other services.
        boolean unchangedLegacyServiceId = counterRequest.getServiceIds() == null
                && Objects.equals(counterRequest.getServiceId(), counter.getServiceId());
        List<String> serviceIds = requestedServiceIds(counterRequest.getServiceIds(), counterRequest.getServiceId());
        if (serviceIds != null && !unchangedLegacyServiceId) {
            counterServiceLinker.setCounterServices(counter, serviceIds);
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

        if (paused) {
            assertNoTokenInProgress(counterId, "pause");
        }
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

        if (StringUtils.hasText(counter.getUserId())) {
            throw new BusinessException("Counter is in use by an agent; release it before deleting");
        }

        counterServiceLinker.unlinkCounter(counter);

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

        if (counter.getStatus() == CounterStatus.SERVING ||
                counter.getStatus() == CounterStatus.CALLING ||
                counter.getStatus() == CounterStatus.COMPLETE) {

            List<Token> activeTokens = tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                    List.of(TokenStatus.SERVING, TokenStatus.CALLING, TokenStatus.REVIEW),
                    counterId
            );

            if (activeTokens.size() > 1) {
                log.warn("Counter {} has {} tokens in active statuses simultaneously — expected at most 1.",
                        counterId, activeTokens.size());
            }

            Token token = pickCurrentToken(counter, activeTokens);
            if (token != null) {
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
            } else {
                response.clearTokenDetails();
            }
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
     * The service list a create/update request asks for: serviceIds when sent,
     * otherwise the single serviceId (blank meaning none), or null when the
     * request doesn't touch services at all.
     */
    private static List<String> requestedServiceIds(List<String> serviceIds, String serviceId) {
        if (serviceIds != null) {
            return serviceIds;
        }
        if (serviceId != null) {
            return StringUtils.hasText(serviceId) ? List.of(serviceId) : List.of();
        }
        return null;
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

        Map<String, List<Token>> activeTokensByCounter = tokenRepository
                .findByStatusInAndAssignedCounterIdIn(ACTIVE_TOKEN_STATUSES, counterIds)
                .stream()
                .collect(Collectors.groupingBy(Token::getAssignedCounterId));

        List<String> serviceIds = Stream.concat(
                        counters.stream().flatMap(c -> CounterServiceLinker.serviceIdsOf(c).stream()),
                        activeTokensByCounter.values().stream().flatMap(List::stream).map(Token::getServiceId))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Services> services = serviceRepository.findAllById(serviceIds);
        Map<String, String> serviceNameById = new HashMap<>();
        Map<String, String> arabicServiceNameById = new HashMap<>();
        for (Services service : services) {
            serviceNameById.put(service.getId(), service.getName());
            arabicServiceNameById.put(service.getId(), service.getArabicName());
        }

        return counters.stream().map(counter -> {
            CounterDisplayDTO dto = new CounterDisplayDTO();
            String name = counter.getName();
            dto.setCounterName(name != null && name.contains(" ")
                    ? name.substring(name.indexOf(" ") + 1)
                    : name);
            dto.setEnabled(counter.getEnabled());
            dto.setPaused(counter.getPaused());
            dto.setCounterStatus(counter.getStatus());

            Token activeToken = pickCurrentToken(counter,
                    activeTokensByCounter.getOrDefault(counter.getId(), List.of()));
            if (activeToken != null) {
                dto.setTokenNumber(activeToken.getToken());
                dto.setCalledAt(activeToken.getStartAt());
                dto.setServiceName(serviceNameById.get(activeToken.getServiceId()));
                dto.setArabicService(arabicServiceNameById.get(activeToken.getServiceId()));
            } else {
                // Idle counter: list every service it handles
                List<String> ids = CounterServiceLinker.serviceIdsOf(counter);
                dto.setServiceName(joinNames(ids, serviceNameById));
                dto.setArabicService(joinNames(ids, arabicServiceNameById));
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Picks the token a counter is actually on out of its SERVING / CALLING /
     * REVIEW tokens. A counter should only ever hold one, but a REVIEW token
     * stays REVIEW until the visitor submits feedback, so when they walk away
     * it lingers on the counter after the agent has moved on. Picking an
     * arbitrary one made the TV show that old token instead of the one being
     * served.
     *
     * SERVING wins over CALLING, which wins over REVIEW, and the newest wins
     * within a status. A REVIEW token only counts while the counter itself is
     * in COMPLETE, i.e. it is the visitor who was just served.
     */
    private Token pickCurrentToken(Counter counter, List<Token> tokens) {
        return tokens.stream()
                .filter(t -> t.getStatus() != TokenStatus.REVIEW
                        || counter.getStatus() == CounterStatus.COMPLETE)
                .min(Comparator
                        .comparingInt((Token t) -> ACTIVE_TOKEN_STATUSES.indexOf(t.getStatus()))
                        .thenComparing(CounterService::lastActivity, Comparator.reverseOrder()))
                .orElse(null);
    }

    private static String joinNames(List<String> ids, Map<String, String> namesById) {
        String joined = ids.stream()
                .map(namesById::get)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" / "));
        return joined.isEmpty() ? null : joined;
    }

    private static LocalDateTime lastActivity(Token t) {
        if (t.getEndAt() != null) return t.getEndAt();
        if (t.getStartAt() != null) return t.getStartAt();
        return t.getCreatedAt() != null ? t.getCreatedAt() : LocalDateTime.MIN;
    }

    // ==================== COUNTER LOGIN / LOGOUT ====================

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
        clone.setServiceIds(original.getServiceIds());
        clone.setStatus(original.getStatus());
        return clone;
    }

    private User loadAgent(AuthenticatedUser currentUser) {
        if (currentUser == null) throw new BusinessException("Not authenticated");
        User user = userRepository.findById(currentUser.id())   // adjust accessor name if needed
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != UserRole.USER) {
            throw new BusinessException("Only counter agents can log in to a counter");
        }
        return user;
    }


    @Transactional(readOnly = true)
    public List<CounterOptionDTO> getPublicCounterList() {
        List<Counter> counters = counterRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .sorted(Comparator.comparing(Counter::getCode))
                .toList();

        List<String> occupantIds = counters.stream().map(Counter::getUserId)
                .filter(StringUtils::hasText).distinct().toList();
        List<String> branchIds = counters.stream().map(Counter::getBranchId)
                .filter(StringUtils::hasText).distinct().toList();

        Map<String, String> userNameById = userRepository.findAllById(occupantIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.toString(u.getName(), "Another agent")));
        Map<String, String> branchNameById = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, b -> Objects.toString(b.getName(), "")));
        // adjust Branch::getId / getName if your entity differs

        return counters.stream().map(c -> {
            boolean occupied = StringUtils.hasText(c.getUserId());
            CounterOptionDTO dto = new CounterOptionDTO();
            dto.setId(c.getId());
            dto.setCode(c.getCode());
            dto.setName(c.getName());
            dto.setBranchId(c.getBranchId());
            dto.setBranchName(branchNameById.get(c.getBranchId()));
            dto.setOccupied(occupied);
            dto.setOccupiedByName(occupied ? userNameById.getOrDefault(c.getUserId(), "Another agent") : null);
            return dto;
        }).toList();
    }

    /**
     * Agent picks a counter. Atomic: only one agent can win a counter.
     * Same agent re-logging in (crash, second device) is allowed and doesn't reset a busy counter.
     */
    @Transactional
    public User claimCounter(String counterId, boolean attachOnly, AuthenticatedUser currentUser) {
        User user = loadAgent(currentUser);
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

        if (!Boolean.TRUE.equals(counter.getEnabled())) {
            throw new BusinessException("Counter " + counter.getName() + " is disabled");
        }
        if (StringUtils.hasText(user.getBranchId()) && !user.getBranchId().equals(counter.getBranchId())) {
            throw new BusinessException("Counter " + counter.getName() + " belongs to a different branch");
        }

        boolean ownedByMe = Objects.equals(counter.getUserId(), user.getId());

        if (attachOnly) {
            if (!ownedByMe) {
                throw new BusinessException("You are not logged in at counter " + counter.getName());
            }
            if (!counterId.equals(user.getCounterId())) {
                user.setCounterId(counterId);
                userRepository.save(user);
            }
            return user;
        }

        String userId = user.getId();
        String previousCounterId = user.getCounterId();

        if (StringUtils.hasText(previousCounterId) && !previousCounterId.equals(counterId)) {
            assertCanLeave(previousCounterId, userId);
        }
        int claimed = counterRepository.claimIfFree(counterId, userId);
        if (claimed == 0) {
            Counter current = counterRepository.findById(counterId)
                    .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));
            String who = StringUtils.hasText(current.getUserId())
                    ? userRepository.findById(current.getUserId()).map(User::getName).orElse("Another agent")
                    : "Another agent";
            throw new BusinessException(who + " is already logged in with this counter");
        }

        // The bulk update cleared the persistence context, so reload.
        Counter fresh = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));
        if (!ownedByMe || fresh.getStatus() == CounterStatus.CLOSED) {
            // A token can still be calling/serving here (e.g. the counter was force-released
            // mid-call). Reflect it instead of showing IDLE: an IDLE counter with a live token
            // let the agent try to log out, the logout was refused and the counter stayed held.
            fresh.setStatus(statusForTokenInProgress(counterId));
            fresh.setPaused(false);
            counterRepository.save(fresh);
        }

        // An agent holds at most one counter: release the previous one (only if still theirs).
        if (StringUtils.hasText(previousCounterId) && !previousCounterId.equals(counterId)) {
            releaseIfOwner(previousCounterId, userId,
                    "Counter released: agent moved to counter " + fresh.getName(), currentUser);
        }

        User me = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        me.setCounterId(counterId);
        userRepository.save(me);

        publishCounterChanged(fresh);
        auditLogService.log(
                AuditAction.COUNTER_STATUS_CHANGED, "Counter", fresh.getId(), fresh.getName(),
                "Agent " + me.getName() + " logged in to counter " + fresh.getName(),
                currentUser, fresh.getBranchId(), request);

        return me;
    }

    private CounterStatus statusForTokenInProgress(String counterId) {
        List<Token> live = tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(IN_PROGRESS, counterId);
        if (live.stream().anyMatch(t -> t.getStatus() == TokenStatus.SERVING)) return CounterStatus.SERVING;
        if (!live.isEmpty()) return CounterStatus.CALLING;
        return CounterStatus.IDLE;
    }

    private void assertCanLeave(String counterId, String userId) {
        Counter c = counterRepository.findById(counterId).orElse(null);
        if (c == null || !Objects.equals(c.getUserId(), userId)) return;
        if (tokenRepository.existsByStatusInAndAssignedCounterId(IN_PROGRESS, counterId)) {
            throw new BusinessException("You still have a token in progress at counter " + c.getName()
                    + ". Log in to that counter to finish, hold or transfer it before moving to another counter");
        }
    }

    /**
     * A counter that is calling/serving a token must not be paused: pausing overwrites
     * the SERVING status and hides the live token from the counter and display board.
     */
    public void assertNoTokenInProgress(String counterId, String action) {
        if (tokenRepository.existsByStatusInAndAssignedCounterId(IN_PROGRESS, counterId)) {
            throw new BusinessException("Cannot " + action + " the counter while a token is being called or served. "
                    + "Finish, hold or transfer it first");
        }
    }

    /**
     * Logout: release whatever counter this agent holds.
     */
    @Transactional
    public void logoutFromCounter(AuthenticatedUser currentUser) {
        if (currentUser == null) return;
        User user = userRepository.findById(currentUser.id()).orElse(null);
        if (user == null) return;

        List<Counter> held = counterRepository.findByUserId(user.getId());
        for (Counter c : held) assertCanLeave(c.getId(), user.getId());
        for (Counter c : held) releaseIfOwner(c.getId(), user.getId(), "Counter closed on logout", currentUser);

        if (user.getCounterId() != null) {
            user.setCounterId(null);
            userRepository.save(user);
        }
    }

    /**
     * Admin escape hatch for a counter stuck on an agent who crashed / left without logging out.
     */
    @Transactional
    public CounterResponseDTO forceReleaseCounter(String counterId, AuthenticatedUser admin) {
        return forceRelease(counterId, admin, "force-released by admin");
    }

    /**
     * Daily sign-out (see DailyLogoutScheduler): releases every held counter the same way
     * an admin force release does, then invalidates every user's login tokens.
     */
    @Transactional
    public int signOutEveryone() {
        List<Counter> held = counterRepository.findByUserIdIsNotNull();
        for (Counter c : held) forceRelease(c.getId(), null, "released by the daily sign-out");
        userRepository.clearAllCounterIds();
        tokenRevocationService.revokeAllUsers();
        return held.size();
    }

    private CounterResponseDTO forceRelease(String counterId, AuthenticatedUser admin, String how) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found with id: " + counterId));

        // A token being called/served here is marked No Show (same as the agent's No Show action)
        // and the counter is left IDLE instead of CLOSED.
        List<Token> inProgress = tokenRepository
                .findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(IN_PROGRESS, counterId);
        for (Token t : inProgress) {
            t.setStatus(TokenStatus.NO_SHOW);
            Token updated = tokenRepository.save(t);
            tokenEventPublisher.publish(new TokenEvent(
                    TokenEventType.TOKEN_NO_SHOW, updated.getBranchId(), updated.getId(),
                    updated.getToken(), counterId, Instant.now()));
            auditLogService.log(
                    AuditAction.TOKEN_NO_SHOW, "Token", updated.getId(), updated.getToken(),
                    "Customer no-show: counter " + counter.getName() + " " + how,
                    admin, updated.getBranchId(), request);
        }
        CounterStatus releasedStatus = inProgress.isEmpty() ? CounterStatus.CLOSED : CounterStatus.IDLE;

        String occupantId = counter.getUserId();
        if (StringUtils.hasText(occupantId)) {
            releaseIfOwner(counterId, occupantId, "Counter " + how, admin, releasedStatus);
            // Sign the agent out everywhere: their next request gets 403 and the
            // app sends them to the login page instead of leaving them on a
            // counter they no longer hold.
            tokenRevocationService.revokeAll(occupantId);
            userRepository.findById(occupantId).ifPresent(u -> {
                if (counterId.equals(u.getCounterId())) {
                    u.setCounterId(null);
                    userRepository.save(u);
                }
            });
        } else if (!inProgress.isEmpty()) {
            counter.setStatus(CounterStatus.IDLE);
            counter.setPaused(false);
            counterRepository.save(counter);
            publishCounterChanged(counter);
        }
        return convertToResponse(counterRepository.findById(counterId).orElse(counter));
    }

    /**
     * Only clears the counter if it is still held by this user (guards against stale tokens/devices).
     */
    private void releaseIfOwner(String counterId, String userId, String reason, AuthenticatedUser actor) {
        releaseIfOwner(counterId, userId, reason, actor, CounterStatus.CLOSED);
    }

    private void releaseIfOwner(String counterId, String userId, String reason, AuthenticatedUser actor,
                                CounterStatus releasedStatus) {
        counterRepository.findById(counterId).ifPresent(counter -> {
            if (!Objects.equals(counter.getUserId(), userId)) return;

            counter.setUserId(null);
            counter.setStatus(releasedStatus);
            counter.setPaused(false);
            counterRepository.save(counter);

            publishCounterChanged(counter);
            auditLogService.log(
                    AuditAction.COUNTER_STATUS_CHANGED, "Counter", counter.getId(), counter.getName(),
                    reason + ": " + counter.getName(), actor, counter.getBranchId(), request);
        });
    }

    private void publishCounterChanged(Counter c) {
        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_STATUS_CHANGED, c.getBranchId(), null, null, c.getId(), Instant.now()));
    }

    /**
     * USER-role agents may only operate the counter they currently hold. Staff roles are unrestricted.
     */
    public void assertCanOperate(String counterId, AuthenticatedUser actor) {
        if (actor == null) throw new BusinessException("Not authenticated");
        if (!"USER".equals(actor.role())) return;
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));
        if (!actor.isSameUser(counter.getUserId())) {
            throw new BusinessException("You are not logged in at counter " + counter.getName());
        }
    }
}