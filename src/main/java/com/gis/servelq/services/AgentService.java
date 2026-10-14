package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.AgentCallResponseDTO;
import com.gis.servelq.dto.RecentServiceDTO;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.dto.TokenTransferRequest;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;
    private final CounterService counterService;

    public List<TokenResponseDTO> getUpcomingTokensForCounter(String counterId) {
        return tokenRepository.findUpcomingTokensForCounter(counterId)
                .stream()
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    // Must be transactional: TokenEventDispatcher is an AFTER_COMMIT listener, so events
    // published outside a transaction are silently dropped (TV never got TOKEN_CALLED).
    // The FOR UPDATE SKIP LOCKED row lock is held until this method commits.
    @Transactional
    public AgentCallResponseDTO callNextToken(String counterId) {
        Token nextToken;
        Counter counter;

        try {
            var claimed = claimNextToken(counterId);
            nextToken = claimed.token();
            counter = claimed.counter();
        } catch (PessimisticLockException | LockTimeoutException ex) {
            throw new BusinessException("Token is being acquired by another counter. Please try again.");
        }

        // Dispatched to sockets after commit
        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_CALLED,
                nextToken.getBranchId(),
                nextToken.getId(),
                nextToken.getToken(),
                counterId,
                Instant.now()
        ));

        if (nextToken.getCounterIds() != null) {
            for (String id : nextToken.getCounterIds()) {
                tokenEventPublisher.publish(new TokenEvent(
                        TokenEventType.AGENT_QUEUE_CHANGED,
                        nextToken.getBranchId(),
                        null,
                        null,
                        id,
                        Instant.now()
                ));
            }
        }

        AgentCallResponseDTO response = AgentCallResponseDTO.fromEntity(nextToken);

        auditLogService.log(
                AuditAction.TOKEN_CALLED,
                "Token",
                nextToken.getId(),
                nextToken.getToken(),
                "Token called to counter: " + counter.getName(),
                getCurrentUser(),
                nextToken.getBranchId(),
                request
        );

        return response;
    }

    private ClaimedToken claimNextToken(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        if (counter.getPaused()) {
            throw new BusinessException("Counter is paused");
        }

        if (CounterServiceLinker.serviceIdsOf(counter).isEmpty()) {
            throw new BusinessException("No service is assigned to this counter");
        }

        Token nextToken = tokenRepository.findNextToken(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("No tokens available"));

        closeOutReviewTokens(counterId);

        nextToken.setStatus(TokenStatus.CALLING);
        nextToken.setAssignedCounterId(counterId);
        nextToken.setAssignedCounterName(counter.getName());
        tokenRepository.save(nextToken);

        counter.setStatus(CounterStatus.CALLING);
        counterRepository.save(counter);

        return new ClaimedToken(nextToken, counter);
    }

    // The previous visitor never submitted feedback, so their token is
    // still REVIEW on this counter. The agent has moved on: close it out
    // so it doesn't linger as this counter's "current" token on the TV.
    private void closeOutReviewTokens(String counterId) {
        tokenRepository.findByStatusInAndAssignedCounterIdOrderByCreatedAtDesc(
                        List.of(TokenStatus.REVIEW), counterId)
                .forEach(t -> {
                    t.setStatus(TokenStatus.DONE);
                    tokenRepository.save(t);
                });
    }

    /**
     * Calls back a visitor who was marked no-show and has returned (procedure
     * 9.e). Any counter that serves the token's service can call it, so the
     * token moves to the calling counter.
     */
    @Transactional
    public AgentCallResponseDTO callNoShowToken(String tokenId, String counterId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getStatus() != TokenStatus.NO_SHOW) {
            throw new BusinessException("Token cannot be called from status: " + token.getStatus());
        }
        if (!LocalDate.now().equals(token.getTokenDate())) {
            throw new BusinessException("Only today's no-show tokens can be called back");
        }

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));
        if (Boolean.TRUE.equals(counter.getPaused())) {
            throw new BusinessException("Counter is paused");
        }
        counterService.assertNoTokenInProgress(counterId, "call a no-show token at");

        closeOutReviewTokens(counterId);

        String previousCounterName = token.getAssignedCounterName();
        token.setStatus(TokenStatus.CALLING);
        token.setAssignedCounterId(counterId);
        token.setAssignedCounterName(counter.getName());
        token.setStartAt(null);
        token.setEndAt(null);
        tokenRepository.save(token);

        counter.setStatus(CounterStatus.CALLING);
        counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_CALLED,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counterId,
                Instant.now()
        ));
        publishQueueChanged(token);

        auditLogService.log(
                AuditAction.TOKEN_REACTIVATED,
                "Token",
                token.getId(),
                token.getToken(),
                "No-show token called back to counter " + counter.getName()
                        + (previousCounterName != null ? " (marked no-show at " + previousCounterName + ")" : ""),
                getCurrentUser(),
                token.getBranchId(),
                request
        );

        return AgentCallResponseDTO.fromEntity(token);
    }

    // Refreshes the agent queue (upcoming, hold and no-show lists) on every
    // counter the token can be served at.
    private void publishQueueChanged(Token token) {
        if (token.getCounterIds() == null) return;
        for (String id : token.getCounterIds()) {
            tokenEventPublisher.publish(new TokenEvent(
                    TokenEventType.AGENT_QUEUE_CHANGED,
                    token.getBranchId(),
                    null,
                    null,
                    id,
                    Instant.now()
            ));
        }
    }

    @Transactional
    public AgentCallResponseDTO callHoldToken(String tokenId) {
        Token token = tokenRepository.findById(tokenId).orElseThrow(() ->
                new ResourceNotFoundException("Token not found"));

        Counter counter = counterRepository.findById(token.getAssignedCounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        if (token.getStatus() != TokenStatus.HOLD) {
            throw new BusinessException("Token cannot be called from status: " + token.getStatus());
        }

        token.setStatus(TokenStatus.CALLING);
        tokenRepository.save(token);

        counter.setStatus(CounterStatus.CALLING);
        counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_HELD,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));

        return AgentCallResponseDTO.fromEntity(token);
    }

    @Transactional
    public void startServingToken(String tokenId) throws Exception {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getAssignedCounterId() == null) throw new Exception("Token not assigned yet");

        if (token.getStatus() != TokenStatus.CALLING) return;

        token.setStatus(TokenStatus.SERVING);
        token.setStartAt(LocalDateTime.now());
        tokenRepository.save(token);


        Counter counter = counterRepository.findById(token.getAssignedCounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));
        counter.setStatus(CounterStatus.SERVING);
        counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_SERVING_STARTED,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));
    }

    @Transactional
    public void completeToken(String tokenId) throws Exception {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getAssignedCounterId() == null) throw new Exception("Token not assigned yet");

        Counter counter = counterRepository.findById(token.getAssignedCounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        token.setStatus(TokenStatus.REVIEW);
        token.setEndAt(LocalDateTime.now());
        tokenRepository.save(token);


        counter.setStatus(CounterStatus.COMPLETE);
        counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_COMPLETED,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));
        auditLogService.log(
                AuditAction.TOKEN_COMPLETED,
                "Token",
                token.getId(),
                token.getToken(),
                "Token completed at counter: " + counter.getName(),
                getCurrentUser(),
                token.getBranchId(),
                request
        );
    }

    public List<RecentServiceDTO> getRecentServices(String counterId) {
        // Only visitors finished today; earlier days belong in the reports.
        return tokenRepository.findTop20ByStatusAndAssignedCounterIdAndEndAtGreaterThanEqualOrderByEndAtDesc(
                        TokenStatus.DONE, counterId, LocalDate.now().atStartOfDay())
                .stream().map(token -> RecentServiceDTO.fromEntity(
                        token.getToken(),
                        token.getMobileNumber(),
                        token.getServiceName(),
                        token.getStartAt(),
                        token.getEndAt()
                )).collect(Collectors.toList());
    }

    @Transactional
    public AgentCallResponseDTO recallToken(String tokenId) throws Exception {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getAssignedCounterId() == null) throw new Exception("Token not assigned yet");

        Counter counter = counterRepository.findById(token.getAssignedCounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        token.setStatus(TokenStatus.CALLING);
        tokenRepository.save(token);

        counter.setStatus(CounterStatus.CALLING);
        counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_CALLED,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));

        return AgentCallResponseDTO.fromEntity(token);
    }

    @Transactional
    public Token transferToken(TokenTransferRequest request) {
        Token token = tokenRepository.findById(request.getTokenId())
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        TransferReason reason = parseTransferReason(request.getReason());

        if (token.getStatus() == TokenStatus.SERVING || token.getStatus() == TokenStatus.CALLING) {
            Counter toCounter = counterRepository.findById(request.getToCounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target counter not found"));
            // Nobody would call the visitor at a counter no agent is logged into.
            if (toCounter.getUserId() == null || toCounter.getUserId().isBlank()) {
                throw new BusinessException("No agent is logged in at counter " + toCounter.getName()
                        + ". Pick a counter that has an agent");
            }

            Counter fromCounter = counterRepository.findById(token.getAssignedCounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target counter not found"));

            fromCounter.setStatus(CounterStatus.IDLE);
            counterRepository.save(fromCounter);

            String fromCounterId = token.getAssignedCounterId();


            token.setIsTransfer(true);
            token.setTransferFrom(fromCounterId);
            token.setTransferReason(reason);

            // Work on a mutable copy: StringListConverter returns List.of() for an
            // empty column, and remove/add on that threw and failed the transfer.
            List<String> counterIds = token.getCounterIds() != null
                    ? new ArrayList<>(token.getCounterIds())
                    : new ArrayList<>();
            if (fromCounterId != null) {
                counterIds.remove(fromCounterId);
            }
            counterIds.add(toCounter.getId());
            token.setCounterIds(counterIds);

            // Set the assigned counter to new one
            token.setAssignedCounterId(toCounter.getId());
            token.setAssignedCounterName(toCounter.getName());

            token.setStatus(TokenStatus.WAITING);
            token.setStartAt(null);
            token.setEndAt(null);

            Token updated = tokenRepository.save(token);
            tokenEventPublisher.publish(new TokenEvent(
                    TokenEventType.TOKEN_TRANSFERRED,
                    token.getBranchId(),
                    token.getId(),
                    token.getToken(),
                    fromCounter.getId(),
                    Instant.now()
            ));
            tokenEventPublisher.publish(new TokenEvent(
                    TokenEventType.AGENT_QUEUE_CHANGED,
                    token.getBranchId(),
                    token.getId(),
                    token.getToken(),
                    toCounter.getId(),
                    Instant.now()
            ));
            auditLogService.log(
                    AuditAction.TOKEN_TRANSFERRED,
                    "Token",
                    updated.getId(),
                    updated.getToken(),
                    "Token transferred from counter " + fromCounter.getName() + " to " + toCounter.getName()
                            + ". Reason: " + reason.getLabel(),
                    getCurrentUser(),
                    updated.getBranchId(),
                    this.request
            );
            return updated;
        } else {
            throw new ResourceNotFoundException("Token cannot be transferred from status: " + token.getStatus());
        }
    }

    private TransferReason parseTransferReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Select a reason for the transfer");
        }
        try {
            return TransferReason.valueOf(reason.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown transfer reason: " + reason);
        }
    }

    @Transactional
    public Token holdToken(String tokenId) {
        Token token = tokenRepository.findById(tokenId).orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getStatus() != TokenStatus.SERVING) {
            throw new ResourceNotFoundException("Token cannot be Hold from status: " + token.getStatus());
        }

        Counter counter = counterRepository.findById(token.getAssignedCounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Target counter not found"));

        counter.setStatus(CounterStatus.IDLE);
        counterRepository.save(counter);

        token.setStatus(TokenStatus.HOLD);
        Token updated = tokenRepository.save(token);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_HELD,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));
        auditLogService.log(
                AuditAction.TOKEN_HELD,
                "Token",
                updated.getId(),
                updated.getToken(),
                "Token put on hold",
                getCurrentUser(),
                updated.getBranchId(),
                request
        );
        return updated;
    }

    // Transactional so TOKEN_NO_SHOW reaches TokenEventDispatcher's AFTER_COMMIT listener.
    @Transactional
    public Token noShow(String tokenId) {
        Token token = tokenRepository.findById(tokenId).orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getStatus() != TokenStatus.SERVING) {
            throw new ResourceNotFoundException("Token cannot be marked no-show from status: " + token.getStatus());
        }

        Counter counter = counterRepository.findById(token.getAssignedCounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Target counter not found"));

        counter.setStatus(CounterStatus.IDLE);
        counterRepository.save(counter);

        token.setStatus(TokenStatus.NO_SHOW);
        Token updated = tokenRepository.save(token);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_NO_SHOW,
                token.getBranchId(),
                token.getId(),
                token.getToken(),
                counter.getId(),
                Instant.now()
        ));
        // Other counters for this service list no-show tokens too.
        publishQueueChanged(token);
        auditLogService.log(
                AuditAction.TOKEN_NO_SHOW,
                "Token",
                updated.getId(),
                updated.getToken(),
                "Customer no-show for token",
                getCurrentUser(),
                updated.getBranchId(),
                request
        );

        return updated;
    }

    public AgentCallResponseDTO getServingOrCallingTokenByCounter(String counterId) {
        Optional<Token> serving = tokenRepository
                .findFirstByAssignedCounterIdAndStatus(counterId, TokenStatus.SERVING);
        Optional<Token> calling = tokenRepository
                .findFirstByAssignedCounterIdAndStatus(counterId, TokenStatus.CALLING);

        Token token = serving.or(() -> calling).orElseThrow(
                () -> new ResourceNotFoundException("No serving or calling token found")
        );

        counterRepository.findById(counterId).orElseThrow(() -> new ResourceNotFoundException("Counter not found"));
        return AgentCallResponseDTO.fromEntity(token);
    }

    private AuthenticatedUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser) {
            return (AuthenticatedUser) auth.getPrincipal();
        }
        return null;
    }

    /**
     * Pause or resume a counter. Only the agent assigned to the counter, or an
     * ADMIN, may do this.
     */
    @Transactional
    public Counter setCounterPaused(String counterId, boolean paused, AuthenticatedUser currentUser) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

        counterService.assertCanOperate(counterId, currentUser);
        if (paused) {
            counterService.assertNoTokenInProgress(counterId, "pause");
        }

        counter.setPaused(paused);
        counter.setStatus(paused ? CounterStatus.PAUSED : CounterStatus.IDLE);
        Counter updated = counterRepository.save(counter);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_STATUS_CHANGED,
                counter.getBranchId(),
                null,
                null,
                counter.getId(),
                Instant.now()
        ));

        auditLogService.log(
                AuditAction.COUNTER_STATUS_CHANGED,
                "Counter",
                updated.getId(),
                updated.getName(),
                (paused ? "Counter paused: " : "Counter resumed: ") + updated.getName(),
                currentUser,
                updated.getBranchId(),
                request
        );

        return updated;
    }

    @Transactional
    public void logout(AuthenticatedUser currentUser) {
        if (currentUser == null || !"USER".equals(currentUser.role())) return;
        counterService.logoutFromCounter(currentUser);
    }


    private void assertOwnsTokenCounter(Token token) {
        if (token.getAssignedCounterId() != null) {
            counterService.assertCanOperate(token.getAssignedCounterId(), getCurrentUser());
        }
    }

    record ClaimedToken(Token token, Counter counter) {
    }
}
