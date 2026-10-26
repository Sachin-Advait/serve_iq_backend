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

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 26, 9, 0);

    private static Token inProgress(String serviceId) {
        Token t = new Token();
        t.setServiceId(serviceId);
        t.setStartAt(NINE);
        return t;
    }

    @Test
    void inProgressTicketIsPendingUntilItsLimitThenBreached() {
        Map<String, Integer> sla = Map.of("s", 600);
        Token t = inProgress("s");
        assertThat(ServiceSlaResolver.servingVerdict(t, sla, NINE.plusMinutes(10)))
                .isEqualTo(ServiceSlaResolver.Verdict.PENDING);
        assertThat(ServiceSlaResolver.servingVerdict(t, sla, NINE.plusMinutes(11)))
                .isEqualTo(ServiceSlaResolver.Verdict.BREACHED);
    }

    @Test
    void rateIsOnTimeOverJudgedAndLeavesPendingOut() {
        Map<String, Integer> sla = Map.of("s", 600);
        Token neverServed = new Token();
        neverServed.setServiceId("s");
        List<Token> tokens = List.of(served("s", 5), served("s", 10), served("s", 11),
                inProgress("s"), neverServed);
        // At 09:05 the in-progress ticket is still within 10 minutes: pending
        ServiceSlaResolver.Tally early = ServiceSlaResolver.servingTally(tokens, sla, NINE.plusMinutes(5));
        assertThat(early.onTime()).isEqualTo(2);
        assertThat(early.breached()).isEqualTo(1);
        assertThat(early.pending()).isEqualTo(1);
        assertThat(early.excluded()).isEqualTo(1);
        assertThat(early.rate()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.01));
        // At 09:30 it has run past its limit: breached, 2 of 4 on time
        assertThat(ServiceSlaResolver.servingTally(tokens, sla, NINE.plusMinutes(30)).rate()).isEqualTo(50.0);
    }

    @Test
    void totalServingAddsEarlierCountersTime() {
        Map<String, Integer> sla = Map.of("s", 600);
        Token t = served("s", 6);
        t.setPriorServingSeconds(5 * 60);
        // Last counter 6 min is on time, whole visit 11 min is breached
        assertThat(ServiceSlaResolver.servingVerdict(t, sla, NINE.plusHours(1)))
                .isEqualTo(ServiceSlaResolver.Verdict.ON_TIME);
        assertThat(ServiceSlaResolver.totalServingVerdict(t, sla, NINE.plusHours(1)))
                .isEqualTo(ServiceSlaResolver.Verdict.BREACHED);
        assertThat(ServiceSlaResolver.totalServingSeconds(t)).isEqualTo(11 * 60);
    }

    @Test
    void transferKeepsServingTimeOfEarlierCounter() {
        Token t = inProgress("s");
        t.setEndAt(NINE.plusMinutes(4));
        t.addServingTimeBeforeTransfer(NINE.plusMinutes(7));
        assertThat(t.getPriorServingSeconds()).isEqualTo(4 * 60);
        Token open = inProgress("s");
        open.addServingTimeBeforeTransfer(NINE.plusMinutes(7));
        assertThat(open.getPriorServingSeconds()).isEqualTo(7 * 60);
    }
}
