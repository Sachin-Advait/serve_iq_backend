package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.TicketIssuingClosedException;
import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The retry around token issuance.
 *
 * Two kiosks in the same branch can derive the same sequence number at the same
 * moment, because it comes from MAX(token_seq)+1. The unique constraint on
 * (branch_id, token_prefix, token_date, token_seq) makes the loser's insert fail
 * instead of quietly handing two customers the same number, and this loop is
 * what turns that failure into the next number up.
 *
 * It could never work before: the single attempt was a private method called on
 * this, so its @Transactional was ignored, and all attempts shared one
 * transaction that was already rollback-only after the first failure.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceRetryTest {

    @Mock private TokenRepository tokenRepository;
    @Mock private TokenIssuer tokenIssuer;
    @Mock private SmsService smsService;
    @Mock private TokenEventPublisher tokenEventPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest request;
    @Mock private ServiceRepository serviceRepository;
    @Mock private BranchService branchService;
    @Mock private TicketCapacityService ticketCapacityService;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(tokenRepository, tokenIssuer, smsService,
                tokenEventPublisher, auditLogService, request, serviceRepository, branchService,
                ticketCapacityService);

        Services service = new Services();
        service.setId("service-1");
        service.setBranchId("branch-1");
        lenient().when(serviceRepository.findById("service-1")).thenReturn(Optional.of(service));
    }

    private TokenRequest request(boolean greenToken) {
        TokenRequest req = new TokenRequest();
        req.setBranchId("branch-1");
        req.setServiceId("service-1");
        req.setPriority(50);
        req.setMobileNumber("91234567");
        req.setGreenToken(greenToken);
        return req;
    }

    private Token issued(String number, int seq) {
        Token t = new Token();
        t.setId("tok-" + seq);
        t.setToken(number);
        t.setTokenSeq(seq);
        t.setTokenDate(LocalDate.now());
        t.setPriority(50);
        t.setBranchId("branch-1");
        t.setServiceId("service-1");
        t.setServiceName("Accounts");
        t.setMobileNumber("91234567");
        t.setStatus(TokenStatus.WAITING);
        t.setCounterIds(List.of("counter-1", "counter-2"));
        return t;
    }

    @Test
    void returnsTheTokenWhenTheFirstAttemptSucceeds() {
        when(tokenIssuer.issueOnce(any())).thenReturn(issued("A001", 1));

        assertThat(tokenService.generateToken(request(false)).getToken()).isEqualTo("A001");
        verify(tokenIssuer, times(1)).issueOnce(any());
    }

    @Test
    void retriesAfterACollisionAndReturnsTheNextNumber() {
        when(tokenIssuer.issueOnce(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(issued("A002", 2));

        assertThat(tokenService.generateToken(request(false)).getToken()).isEqualTo("A002");
        verify(tokenIssuer, times(2)).issueOnce(any());
    }

    @Test
    void keepsRetryingThroughSeveralCollisions() {
        when(tokenIssuer.issueOnce(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(issued("A004", 4));

        assertThat(tokenService.generateToken(request(false)).getToken()).isEqualTo("A004");
        verify(tokenIssuer, times(4)).issueOnce(any());
    }

    @Test
    void givesUpWithAClearErrorRatherThanLoopingForever() {
        when(tokenIssuer.issueOnce(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> tokenService.generateToken(request(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Could not allocate a token number");
    }

    @Test
    void publishesCreatedPlusOneQueueEventPerCounter() {
        when(tokenIssuer.issueOnce(any())).thenReturn(issued("A001", 1));

        tokenService.generateToken(request(false));

        ArgumentCaptor<TokenEvent> events = ArgumentCaptor.forClass(TokenEvent.class);
        verify(tokenEventPublisher, times(3)).publish(events.capture());

        assertThat(events.getAllValues().get(0).getType()).isEqualTo(TokenEventType.TOKEN_CREATED);
        assertThat(events.getAllValues())
                .filteredOn(e -> e.getType() == TokenEventType.AGENT_QUEUE_CHANGED)
                .hasSize(2);
    }

    @Test
    void greenTokenSendsTheReceiptSmsAsynchronously() {
        when(tokenIssuer.issueOnce(any())).thenReturn(issued("A001", 1));

        tokenService.generateToken(request(true));

        verify(smsService).sendReceiptSmsAsync(any());
    }

    @Test
    void noSmsWhenGreenTokenIsNotRequested() {
        when(tokenIssuer.issueOnce(any())).thenReturn(issued("A001", 1));

        tokenService.generateToken(request(false));

        verifyNoInteractions(smsService);
    }

    @Test
    void issuesNothingWhileTicketIssuingIsClosed() {
        doThrow(new TicketIssuingClosedException("Ticket issuing has stopped"))
                .when(branchService).assertTicketIssuingOpen(eq("branch-1"), any());

        assertThatThrownBy(() -> tokenService.generateToken(request(false)))
                .isInstanceOf(TicketIssuingClosedException.class);
        verifyNoInteractions(tokenIssuer);
    }
}
