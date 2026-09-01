package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.SmsRequest;
import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter RECEIPT_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter RECEIPT_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TokenRepository tokenRepository;
    private final TokenIssuer tokenIssuer;
    private final SmsService smsService;
    private final TokenEventPublisher tokenEventPublisher;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;
    private final ServiceRepository serviceRepository;

    /**
     * Issues a token, retrying if another kiosk grabbed the same sequence number
     * first.
     * <p>
     * Two things were wrong before. The retry caught DataIntegrityViolationException
     * but no unique constraint existed, so a duplicate never threw and two people
     * were simply handed the same number. And this method was @Transactional
     * around a private, self-invoked createTokenOnce, so all three "attempts" ran
     * in one transaction that was already rollback-only after the first failure.
     * <p>
     * Now the constraint exists (see the Token entity and V1 migration), each
     * attempt runs in its own transaction via TokenIssuer, and this method is
     * deliberately NOT transactional so a failed attempt does not poison the next.
     */
    public TokenResponseDTO generateToken(TokenRequest request) {
        Token savedToken = null;

        Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

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

        // People currently WAITING (i.e. unassigned to a counter) for this
        // service - includes the token just issued above, since
        // tokenIssuer.issueOnce() runs in its own REQUIRES_NEW transaction
        // that has already committed by the time we get here.
        long waitingCount = tokenRepository.countByServiceIdAndStatus(
                savedToken.getServiceId(), TokenStatus.WAITING);

        // Sent after the token is committed and off this thread, via
        // SmsService.sendReceiptSmsAsync. That method is @Async on a
        // DIFFERENT bean (SmsService), so the call from here goes through
        // Spring's proxy and actually runs asynchronously - unlike the
        // private self-invocation bug fixed in TokenIssuer, this crosses a
        // real bean boundary. It used to run inside the transaction, so a
        // pooled DB connection was held open for the whole round trip, and a
        // rollback afterwards still left the customer with a message about a
        // token that did not exist.
        if (Boolean.TRUE.equals(request.getGreenToken()) && savedToken.getMobileNumber() != null) {
            smsService.sendReceiptSmsAsync(buildSmsRequest(
                    savedToken,
                    waitingCount,
                    service.getArabicName(),
                    request.getLanguage()
            ));
        }

        return TokenResponseDTO.fromEntity(savedToken, waitingCount);
    }

    private SmsRequest buildSmsRequest(Token token, long waitingCount, String serviceArabicName, String lang) {
        SmsRequest smsRequest = new SmsRequest();
        smsRequest.setTo(token.getMobileNumber());
        smsRequest.setTokenNo(token.getToken());
        smsRequest.setServiceEn(token.getServiceName());
        smsRequest.setServiceAr(serviceArabicName);
        smsRequest.setCurrentQueue(String.valueOf(waitingCount));
        if (token.getCreatedAt() != null) {
            smsRequest.setDate(RECEIPT_DATE_FMT.format(token.getCreatedAt()));
            smsRequest.setTime(RECEIPT_TIME_FMT.format(token.getCreatedAt()));
        }
        smsRequest.setQrValue(TokenResponseDTO.buildQrValue(token));
        // TokenRequest has no language field yet; defaulting to English.
        smsRequest.setLanguage(lang);
        return smsRequest;
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