package com.gis.servelq.services;

import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.repository.ServiceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServingSlaTest {

    private static Services service(String id, String parentId, Integer servingSla) {
        Services s = new Services();
        s.setId(id);
        s.setParentId(parentId);
        s.setServingSlaSeconds(servingSla);
        return s;
    }

    private static Token served(String serviceId, int minutes) {
        Token t = new Token();
        t.setServiceId(serviceId);
        LocalDateTime start = LocalDateTime.of(2026, 10, 26, 9, 0);
        t.setStartAt(start);
        t.setEndAt(start.plusMinutes(minutes));
        return t;
    }

    @Test
    void ownThenParentThenTenMinuteDefault() {
        ServiceRepository repo = mock(ServiceRepository.class);
        when(repo.findByBranchId("b")).thenReturn(List.of(
                service("main", null, 300),
                service("sub", "main", null),
                service("own", "main", 1200),
                service("other", null, null)));
        Map<String, Integer> sla = new ServiceSlaResolver(repo).servingForBranch("b");
        assertThat(sla).containsEntry("main", 300).containsEntry("sub", 300)
                .containsEntry("own", 1200).containsEntry("other", 600);
    }

    @Test
    void rateCountsOnlyFinishedTickets() {
        Map<String, Integer> sla = Map.of("s", 600);
        Token unfinished = new Token();
        unfinished.setServiceId("s");
        unfinished.setStartAt(LocalDateTime.now());
        List<Token> tokens = List.of(served("s", 5), served("s", 10), served("s", 11), unfinished);
        assertThat(ServiceSlaResolver.servingSlaRate(tokens, sla)).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.01));
        assertThat(ServiceSlaResolver.withinServingSla(unfinished, sla)).isFalse();
    }
}
