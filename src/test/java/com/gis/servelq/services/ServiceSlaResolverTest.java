package com.gis.servelq.services;

import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceSlaResolverTest {

    @Mock private ServiceRepository serviceRepository;

    private static Services service(String id, String parentId, Integer slaSeconds) {
        Services s = new Services();
        s.setId(id);
        s.setParentId(parentId);
        s.setSlaSeconds(slaSeconds);
        return s;
    }

    private static Token token(String serviceId, long waitSeconds) {
        Token t = new Token();
        LocalDateTime created = LocalDateTime.of(2026, 10, 25, 9, 0);
        t.setServiceId(serviceId);
        t.setCreatedAt(created);
        t.setStartAt(created.plusSeconds(waitSeconds));
        return t;
    }

    @Test
    void subServiceUsesOwnThenParentThenDefault() {
        when(serviceRepository.findByBranchId("BR")).thenReturn(List.of(
                service("main", null, 600),
                service("own", "main", 300),
                service("inherit", "main", null),
                service("bare", null, null),
                service("orphan", "bare", null)));

        Map<String, Integer> sla = new ServiceSlaResolver(serviceRepository).forBranch("BR");

        assertThat(sla).containsEntry("main", 600)
                .containsEntry("own", 300)
                .containsEntry("inherit", 600)
                .containsEntry("bare", ServiceSlaResolver.DEFAULT_SLA_SECONDS)
                .containsEntry("orphan", ServiceSlaResolver.DEFAULT_SLA_SECONDS);
    }

    private static ServiceSlaResolver.Verdict verdict(Token t, Map<String, Integer> sla) {
        return ServiceSlaResolver.waitingVerdict(t, sla, LocalDateTime.of(2026, 10, 25, 12, 0));
    }

    @Test
    void ticketIsJudgedAgainstItsServiceSla() {
        Map<String, Integer> sla = Map.of("quick", 300);

        assertThat(verdict(token("quick", 300), sla)).isEqualTo(ServiceSlaResolver.Verdict.ON_TIME);
        assertThat(verdict(token("quick", 301), sla)).isEqualTo(ServiceSlaResolver.Verdict.BREACHED);
        // Unknown service falls back to the default 15 minutes
        assertThat(verdict(token("gone", 900), sla)).isEqualTo(ServiceSlaResolver.Verdict.ON_TIME);
        assertThat(verdict(token("gone", 901), sla)).isEqualTo(ServiceSlaResolver.Verdict.BREACHED);
    }

    @Test
    void uncalledTicketIsPendingThenBreachedOnceItsLimitExpires() {
        Map<String, Integer> sla = Map.of("quick", 300);
        Token t = new Token();
        t.setServiceId("quick");
        t.setStatus(TokenStatus.WAITING);
        LocalDateTime created = LocalDateTime.of(2026, 10, 25, 9, 0);
        t.setCreatedAt(created);

        assertThat(ServiceSlaResolver.waitingVerdict(t, sla, created.plusMinutes(5)))
                .isEqualTo(ServiceSlaResolver.Verdict.PENDING);
        assertThat(ServiceSlaResolver.waitingVerdict(t, sla, created.plusMinutes(6)))
                .isEqualTo(ServiceSlaResolver.Verdict.BREACHED);

        // A no-show record without any call time cannot be judged
        t.setStatus(TokenStatus.NO_SHOW);
        assertThat(ServiceSlaResolver.waitingVerdict(t, sla, created.plusMinutes(6)))
                .isEqualTo(ServiceSlaResolver.Verdict.EXCLUDED);
    }

    @Test
    void waitingAndServingAreJudgedIndependently() {
        Token t = token("quick", 60);
        t.setEndAt(t.getStartAt().plusMinutes(20));
        LocalDateTime asOf = LocalDateTime.of(2026, 10, 25, 12, 0);
        assertThat(ServiceSlaResolver.waitingVerdict(t, Map.of("quick", 300), asOf))
                .isEqualTo(ServiceSlaResolver.Verdict.ON_TIME);
        assertThat(ServiceSlaResolver.servingVerdict(t, Map.of("quick", 600), asOf))
                .isEqualTo(ServiceSlaResolver.Verdict.BREACHED);
    }
}
