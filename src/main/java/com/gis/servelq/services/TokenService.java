package com.gis.servelq.services;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final VisitorRepository visitorRepository;
    private final CounterRepository counterRepository;

    @Transactional
    public TokenResponse generateToken(TokenRequest request) {
        // Validate service and branch
        ServiceModel serviceModel = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        // Create or find visitor
        Visitor visitor = null;
        if (request.getCivilId() != null && !request.getCivilId().isEmpty()) {
            visitor = visitorRepository.findByCivilId(request.getCivilId())
                    .orElseGet(() -> {
                        Visitor newVisitor = new Visitor();
                        newVisitor.setCivilId(request.getCivilId());
                        return visitorRepository.save(newVisitor);
                    });
        }

        // Generate token number
        String tokenNumber = generateTokenNumber(branch, serviceModel);

        // Create token
        Token token = new Token();
        token.setToken(tokenNumber);
        token.setBranchId(branch.getId());
        token.setServiceId(serviceModel.getId());
        token.setPriority(request.getPriority());
        token.setStatus(TokenStatus.WAITING);

        if (visitor != null) {
            token.setVisitorId(visitor.getId());
        }

        Token savedToken = tokenRepository.save(token);

        // Calculate waiting count
        long waitingCount = tokenRepository.countByServiceIdAndStatus(serviceModel.getId(), TokenStatus.WAITING);

        TokenResponse response = getTokenResponse(savedToken, serviceModel, waitingCount);

        return response;
    }

    private static TokenResponse getTokenResponse(Token savedToken, ServiceModel serviceModel, long waitingCount) {
        TokenResponse response = new TokenResponse();
        response.setId(savedToken.getId());
        response.setToken(savedToken.getToken());
        response.setServiceName(serviceModel.getName());
        response.setServiceCode(serviceModel.getCode());
        response.setStatus(savedToken.getStatus());
        response.setWaitingCount((int) waitingCount);
        response.setCreatedAt(savedToken.getCreatedAt());

        // Calculate estimated time (simplified)
        if (serviceModel.getSlaSec() != null) {
            response.setEstimatedTime(
                    savedToken.getCreatedAt().plusSeconds(serviceModel.getSlaSec() * waitingCount)
            );
        }
        return response;
    }

    public Token getTokenById(String tokenId) {
        return tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));
    }

    private String generateTokenNumber(Branch branch,  ServiceModel serviceModel) {
        String prefix = serviceModel.getCode() + "-";

        Optional<Integer> lastNumber = tokenRepository.findLastTokenNumber(
                branch.getId(), serviceModel.getId(), prefix);

        int nextNumber = lastNumber.orElse(0) + 1;
        return prefix + String.format("%03d", nextNumber);
    }

    public List<TokenDTO> getUpcomingTokensForCounter(String counterId) {
        return tokenRepository.findUpcomingTokensForCounter(counterId)
                .stream()
                .map(TokenDTO::fromEntity) // convert each Token to TokenDTO
                .toList();
    }

    @Transactional
    public AgentCallResponse callNextToken(String counterId) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new RuntimeException("Counter not found"));

        if (counter.getPaused()) {
            throw new RuntimeException("Counter is paused");
        }

        // Find the next token for this counter's services
        List<ServiceModel> counterServiceModels = counter.getServices();
        Token nextToken = null;

        for ( ServiceModel serviceModel : counterServiceModels) {
            Optional<Token> token = tokenRepository
                    .findFirstByServiceIdAndStatusOrderByPriorityAscCreatedAtAsc(
                            serviceModel.getId(), TokenStatus.WAITING);

            if (token.isPresent()) {
                if (nextToken == null || token.get().getPriority() < nextToken.getPriority() ||
                        (token.get().getPriority().equals(nextToken.getPriority()) &&
                                token.get().getCreatedAt().isBefore(nextToken.getCreatedAt()))) {
                    nextToken = token.get();
                }
            }
        }

        if (nextToken == null) {
            throw new RuntimeException("No tokens available");
        }

        // Update token status
        nextToken.setStatus(TokenStatus.CALLING);
        nextToken.setCounterId(counterId);
        nextToken.setCalledAt(LocalDateTime.now());
        tokenRepository.save(nextToken);

        // Update counter status
        counter.setStatus("CALLING");
        counterRepository.save(counter);

        // Prepare response
        AgentCallResponse response = new AgentCallResponse();
        response.setTokenId(nextToken.getId());
        response.setToken(nextToken.getToken());
        response.setServiceName(nextToken.getService().getName());
        response.setCounterId(counter.getId());
        response.setCounterName(counter.getName());
        response.setCalledAt(nextToken.getCalledAt());

        if (nextToken.getVisitor() != null) {
            response.setVisitorName(nextToken.getVisitor().getCivilId());
        }

        // Calculate waiting count for the service
        long waitingCount = tokenRepository.countByServiceIdAndStatus(
                nextToken.getServiceId(), TokenStatus.WAITING);
        response.setWaitingCount((int) waitingCount);

        return response;
    }

    @Transactional
    public void startServingToken(String tokenId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        token.setStatus(TokenStatus.SERVING);
        token.setStartAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Update counter status
        if (token.getCounterId() != null) {
            Counter counter = counterRepository.findById(token.getCounterId())
                    .orElseThrow(() -> new RuntimeException("Counter not found"));
            counter.setStatus("SERVING");
            counterRepository.save(counter);
        }
    }

    @Transactional
    public void completeToken(String tokenId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        token.setStatus(TokenStatus.DONE);
        token.setEndAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Update counter status
        if (token.getCounterId() != null) {
            Counter counter = counterRepository.findById(token.getCounterId())
                    .orElseThrow(() -> new RuntimeException("Counter not found"));
            counter.setStatus("IDLE");
            counterRepository.save(counter);
        }
    }

    public List<LiveTokenDTO> getLatestCalledTokens() {
        return tokenRepository.findByStatus(TokenStatus.CALLING)
                .stream()
                .map(LiveTokenDTO::fromEntity)
                .toList();
    }

    public List<LiveTokenDTO> getServingTokens() {
        return tokenRepository.findByStatus(TokenStatus.SERVING)
                .stream()
                .map(LiveTokenDTO::fromEntity)
                .toList();
    }
}
