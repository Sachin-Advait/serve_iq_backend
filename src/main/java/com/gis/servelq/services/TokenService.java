package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.Token;
import com.gis.servelq.repository.TokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int MAX_ATTEMPTS = 5;

    private final TokenRepository tokenRepository;
    private final TokenIssuer tokenIssuer;
    private final WhatsAppService whatsAppService;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    /**
     * Issues a token, retrying if another kiosk grabbed the same sequence number
     * first.
     *
     * Two things were wrong before. The retry caught DataIntegrityViolationException
     * but no unique constraint existed, so a duplicate never threw and two people
     * were simply handed the same number. And this method was @Transactional
     * around a private, self-invoked createTokenOnce, so all three "attempts" ran
     * in one transaction that was already rollback-only after the first failure.
     *
     * Now the constraint exists (see the Token entity and V1 migration), each
     * attempt runs in its own transaction via TokenIssuer, and this method is
     * deliberately NOT transactional so a failed attempt does not poison the next.
     */
    public TokenResponseDTO generateToken(TokenRequest request) {
        Token savedToken = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                savedToken = tokenIssuer.issueOnce(request);
                break;
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new BusinessException(
                            "Could not allocate a token number after " + MAX_ATTEMPTS + " attempts");
                }
                log.debug("Token sequence collision on attempt {}, retrying", attempt);
                sleepBriefly();
            }
        }

        publishTokenEvents(savedToken);
        auditLogService.log(
                AuditAction.TOKEN_GENERATED,
                "Token",
                savedToken.getId(),
                savedToken.getToken(),
                "Token generated for service: " + savedToken.getServiceName(),
                null,
                savedToken.getBranchId(),
                this.request
        );

        // Sent after the token is committed and off this thread. It used to run
        // inside the transaction, so a pooled DB connection was held open for the
        // whole Twilio round trip, and a rollback afterwards still left the
        // customer with a message about a token that did not exist.
        if (Boolean.TRUE.equals(request.getGreenToken()) && request.getMobileNumber() != null) {
            whatsAppService.sendTokenNotificationAsync(request.getMobileNumber(), savedToken.getToken());
        }


        return TokenResponseDTO.fromEntity(savedToken);
    }

    private void publishTokenEvents(Token savedToken) {
        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TOKEN_CREATED,
                savedToken.getBranchId(),
                savedToken.getId(),
                savedToken.getToken(),
                null,
                Instant.now()
        ));

        if (savedToken.getCounterIds() != null) {
            for (String counterId : savedToken.getCounterIds()) {
                tokenEventPublisher.publish(new TokenEvent(
                        TokenEventType.AGENT_QUEUE_CHANGED,
                        savedToken.getBranchId(),
                        null,
                        null,
                        counterId,
                        Instant.now()
                ));
            }
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Token generation interrupted");
        }
    }

    public Token getTokenById(String tokenId) {
        return tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found: " + tokenId));
    }

}
