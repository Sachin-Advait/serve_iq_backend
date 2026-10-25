package com.gis.servelq.services;

import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
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

    @Test
    void ticketIsJudgedAgainstItsServiceSla() {
        Map<String, Integer> sla = Map.of("quick", 300);

        assertThat(ServiceSlaResolver.withinSla(token("quick", 300), sla)).isTrue();
        assertThat(ServiceSlaResolver.withinSla(token("quick", 301), sla)).isFalse();
        // Unknown service falls back to the default 15 minutes
        assertThat(ServiceSlaResolver.withinSla(token("gone", 900), sla)).isTrue();
        assertThat(ServiceSlaResolver.withinSla(token("gone", 901), sla)).isFalse();
    }
}
