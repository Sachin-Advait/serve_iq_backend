package com.gis.servelq.services;

import com.gis.servelq.dto.TabularReportDTO;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsReportServiceTest {

    @Mock private TokenRepository tokenRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private CounterRepository counterRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private CounterBreakRepository counterBreakRepository;
    @Mock private ServiceSlaResolver serviceSlaResolver;

    private OperationsReportService service;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime NINE = DAY.atTime(9, 0);

    @BeforeEach
    void setUp() {
        service = new OperationsReportService(tokenRepository, branchRepository, counterRepository,
                serviceRepository, userRepository, userSessionRepository, counterBreakRepository, serviceSlaResolver);
        Branch branch = new Branch();
        branch.setId("BR");
        branch.setName("Main");
        lenient().when(branchRepository.findById("BR")).thenReturn(Optional.of(branch));
        lenient().when(serviceSlaResolver.forBranch("BR")).thenReturn(Map.of("S", 600));
        User agent = new User();
        agent.setId("U1");
        agent.setName("Asma");
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of(agent));
    }

    private static Token token(String name, int waitMin, Integer serveMin, TokenStatus status) {
        Token t = new Token();
        t.setToken(name);
        t.setServiceId("S");
        t.setServiceName("Pensions");
        t.setCreatedAt(NINE);
        t.setStatus(status);
        t.setServedByUserId("U1");
        t.setAssignedCounterId("C1");
        t.setAssignedCounterName("Counter 1");
        if (status != TokenStatus.WAITING) {
            t.setFirstCalledAt(NINE.plusMinutes(waitMin));
            t.setCallCount(1);
        }
        if (serveMin != null) {
            t.setStartAt(NINE.plusMinutes(waitMin + 1));
            t.setEndAt(NINE.plusMinutes(waitMin + 1 + serveMin));
        }
        return t;
    }

    private static String stat(TabularReportDTO r, String label) {
        return r.getSummary().stream().filter(s -> s.getLabel().equals(label)).findFirst().orElseThrow().getValue();
    }

    @Test
    void waitingReportJudgesEachTicketAgainstItsServiceSla() {
        when(tokenRepository.findByBranchIdAndCreatedAtBetween(eq("BR"), any(), any())).thenReturn(List.of(
                token("A001", 5, 4, TokenStatus.DONE),
                token("A002", 12, 4, TokenStatus.DONE),     // over the 10 min SLA
                token("A003", 0, null, TokenStatus.WAITING))); // never called: past SLA by end of day

        TabularReportDTO r = service.waitingAndSla("BR", DAY, DAY);

        assertThat(stat(r, "SLA breaches")).isEqualTo("2");
        assertThat(stat(r, "Not called yet")).isEqualTo("1");
        assertThat(stat(r, "Average wait")).isEqualTo("8.5 min");
        assertThat(r.getSections().stream().filter(x -> x.getTitle().equals("Longest delays"))
                .findFirst().orElseThrow().getRows()).hasSize(2);
        // Both finished tickets took 4 min, inside the 10 min default serving SLA
        assertThat(stat(r, "Served over serving SLA")).isEqualTo("0");
        assertThat(stat(r, "Within serving SLA (last counter)")).startsWith("100.0%");
    }

    @Test
    void noShowCountsCalledBackTickets() {
        Token back = token("A001", 2, 5, TokenStatus.DONE);
        back.setNoShowCount(1);
        back.setNoShowUserId("U1");
        back.setCallCount(3);
        when(tokenRepository.findByBranchIdAndCreatedAtBetween(eq("BR"), any(), any())).thenReturn(List.of(
                back, token("A002", 2, 5, TokenStatus.DONE)));

        TabularReportDTO r = service.noShowAnalysis("BR", DAY, DAY);

        assertThat(stat(r, "No-shows")).isEqualTo("1");
        assertThat(stat(r, "Called back and served")).isEqualTo("1");
        assertThat(stat(r, "Extra calls")).isEqualTo("2");
    }

    @Test
    void utilizationSplitsLoggedInTimeIntoServingBreaksAndIdle() {
        when(tokenRepository.findByBranchIdAndCreatedAtBetween(eq("BR"), any(), any())).thenReturn(List.of(
                token("A001", 0, 60, TokenStatus.DONE)));
        UserSession s = new UserSession();
        s.setUserId("U1");
        s.setCounterId("C1");
        s.setLoginAt(NINE);
        s.setLogoutAt(NINE.plusHours(4));
        when(userSessionRepository.findOverlapping(eq("BR"), eq(List.of(UserRole.USER, UserRole.AGENT)), any(), any())).thenReturn(List.of(s));
        CounterBreak b = new CounterBreak();
        b.setCounterId("C1");
        b.setUserId("U1");
        b.setStartedAt(NINE.plusHours(2));
        b.setEndedAt(NINE.plusHours(2).plusMinutes(30));
        when(counterBreakRepository.findOverlapping(eq("BR"), any(), any())).thenReturn(List.of(b));
        Counter c = new Counter();
        c.setId("C1");
        c.setName("Counter 1");
        when(counterRepository.findByBranchIdOrderByCreatedAtAsc("BR")).thenReturn(List.of(c));

        TabularReportDTO r = service.agentUtilization("BR", DAY, DAY);

        // 240 logged in, 60 serving, 30 break, 150 idle, 60/210 utilization
        assertThat(r.getSections().get(0).getRows().get(0))
                .containsExactly("Asma", 1L, 240.0, 60.0, 150.0, 1, 30.0, 28.6);
        assertThat(r.getSections().get(1).getRows().get(0).subList(0, 7))
                .containsExactly("Counter 1", 240.0, 1, 30.0, 210.0, 87.5, 28.6);
    }

    @Test
    void overlapClipsToRangeAndRunsOpenEndedToCap() {
        LocalDateTime from = NINE, to = NINE.plusHours(1);
        assertThat(OperationsReportService.overlapMinutes(NINE.minusHours(1), NINE.plusMinutes(30), from, to)).isEqualTo(30);
        assertThat(OperationsReportService.overlapMinutes(NINE.plusMinutes(45), null, from, to)).isEqualTo(15);
        assertThat(OperationsReportService.overlapMinutes(to, null, from, to)).isZero();
    }
}
