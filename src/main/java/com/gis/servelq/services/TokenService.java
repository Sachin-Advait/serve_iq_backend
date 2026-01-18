package com.gis.servelq.services;

import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final WhatsAppService whatsAppService;
    private final CategoryService categoryService;
    private final TokenEventPublisher tokenEventPublisher;

    @Transactional
    public TokenResponseDTO generateToken(TokenRequest request) {
        int attempts = 0;
        int maxAttempts = 3;

        while (true) {
            try {
                return createTokenOnce(request);
            } catch (DataIntegrityViolationException e) {
                if (++attempts >= maxAttempts) {
                    throw new RuntimeException("Failed to generate token after " + maxAttempts + " attempts", e);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Token generation interrupted", ie);
                }
            }
        }
    }

    public Token getTokenById(String tokenId) {
        return tokenRepository.findById(tokenId).orElseThrow(() -> new RuntimeException("Token not found"));
    }

    @Transactional
    private TokenResponseDTO createTokenOnce(TokenRequest request) {
        Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        TokenNumber tokenNumber = generateToken(branch, service.getCode(), request.getPriority());

        Token token = new Token();
        token.setToken(tokenNumber.token());
        token.setTokenSeq(tokenNumber.seq());
        token.setPriority(tokenNumber.priority());

        token.setBranchId(branch.getId());
        token.setServiceId(service.getId());
        token.setServiceName(service.getName());

        token.setStatus(TokenStatus.WAITING);
        token.setMobileNumber(request.getMobileNumber());
        token.setCounterIds(service.getCounterIds());

        Token savedToken = tokenRepository.saveAndFlush(token);

        if (request.getGreenToken() == true) {
            sendWhatsAppNotification(request.getMobileNumber(), savedToken.getToken());
        }

        // Publish token created
        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_CREATED,
                branch.getId(),
                savedToken.getId(),
                savedToken.getToken(),
                null,
                Instant.now()
        ));

        // Publish agent queue updates
        if (service.getCounterIds() != null) {
            for (String counterId : service.getCounterIds()) {
                tokenEventPublisher.publish(new TokenEvent(
                        TokenEventType.AGENT_QUEUE_CHANGED,
                        branch.getId(),
                        null,
                        null,
                        counterId,
                        Instant.now()
                ));
            }
        }


        return TokenResponseDTO.fromEntity(savedToken);
    }

    private void sendWhatsAppNotification(String mobileNumber, String tokenNumber) {
        String phoneNumber = "+968" + mobileNumber;
        String message = String.format(
                "Your token number %s has been generated successfully.%nPlease monitor the TV display for your turn.",
                tokenNumber
        );
        whatsAppService.sendMessage(phoneNumber, message);
    }

    private TokenNumber generateToken(Branch branch, String serviceCode, Integer requestPriority) {
        LocalDate today = LocalDate.now();

        int priority = requestPriority != null ? requestPriority : 50;

        int nextSeq = tokenRepository.findLastSeqForPriorityToday(
                branch.getId(),
                priority,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        ).orElse(0) + 1;

        String prefix = resolveTokenPrefix(serviceCode, priority);
        String token = prefix + String.format("%03d", nextSeq);

        return new TokenNumber(token, nextSeq, priority);
    }

    private String resolveTokenPrefix(String serviceCode, int priority) {
        if (priority == 50) return serviceCode.trim().toUpperCase();

        return categoryService.findByPriority(priority)
                .map(category -> category.getCode().trim().toUpperCase())
                .orElse(serviceCode.trim().toUpperCase());
    }

    private record TokenNumber(String token, int seq, int priority) {
    }
}
