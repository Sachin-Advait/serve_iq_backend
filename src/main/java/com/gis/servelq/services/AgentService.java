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
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.gis.servelq.security.AuthenticatedUser;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;
    private final CounterService counterService;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    public List<TokenResponseDTO> getUpcomingTokensForCounter(String counterId) {
        return tokenRepository.findUpcomingTokensForCounter(counterId)
                .stream()
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public AgentCallResponseDTO callNextToken(String counterId) {
        try {
            Counter counter = counterRepository.findById(counterId)
                    .orElseThrow(() -> new ResourceNotFoundException("Counter not found"));

            if (counter.getPaused()) {
                throw new BusinessException("Counter is paused");
            }

            serviceRepository.findById(counter.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

            Optional<Token> optionalToken = tokenRepository.findNextToken(counterId);

            if (optionalToken.isEmpty()) {
                throw new ResourceNotFoundException("No tokens available");
            }

            Token nextToken = optionalToken.get();

            nextToken.setStatus(TokenStatus.CALLING);
            nextToken.setAssignedCounterId(counterId);
            nextToken.setAssignedCounterName(counter.getName());
            tokenRepository.save(nextToken);

            counter.setStatus(CounterStatus.CALLING);
            counterRepository.save(counter);

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

            // Log token called
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
        } catch (PessimisticLockException | LockTimeoutException ex) {
            throw new BusinessException(
                    "Token is being acquired by another counter. Please try again."
            );
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
        return tokenRepository.findTop20ByStatusAndAssignedCounterIdOrderByEndAtDesc(TokenStatus.DONE, counterId)
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

        if (token.getStatus() == TokenStatus.SERVING || token.getStatus() == TokenStatus.CALLING) {
            Counter toCounter = counterRepository.findById(request.getToCounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target counter not found"));

            Counter fromCounter = counterRepository.findById(token.getAssignedCounterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target counter not found"));

            fromCounter.setStatus(CounterStatus.IDLE);
            counterRepository.save(fromCounter);

            String fromCounterId = token.getAssignedCounterId();


            token.setIsTransfer(true);
            token.setTransferFrom(fromCounterId);

            // -----------------------------------------
            // REMOVE OLD COUNTER FROM counterIds LIST
            // -----------------------------------------
            if (token.getCounterIds() != null && fromCounterId != null) {
                token.getCounterIds().remove(fromCounterId);
            }
            // -----------------------------------------

            // ADD NEW COUNTER IF YOU WANT (optional)
            if (token.getCounterIds() != null) {
                token.getCounterIds().add(toCounter.getId());
            }

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
                    "Token transferred from counter " + fromCounter.getName() + " to " + toCounter.getName(),
                    getCurrentUser(),
                    updated.getBranchId(),
                    this.request
            );
            return updated;
        } else {
            throw new ResourceNotFoundException("Token cannot be transferred from status: " + token.getStatus());
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


    public Token noShow(String tokenId) {
        Token token = tokenRepository.findById(tokenId).orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getStatus() != TokenStatus.SERVING) {
            throw new ResourceNotFoundException("Token cannot be Hold from status: " + token.getStatus());
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
}
