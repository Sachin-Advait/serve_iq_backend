package com.gis.servelq.services;

import com.gis.servelq.Exceptions.TicketIssuingClosedException;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCapacityServiceTest {

    @Mock private BranchRepository branchRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private CounterRepository counterRepository;

    private TicketCapacityService service;
    private Branch branch;

    @BeforeEach
    void setUp() {
        service = new TicketCapacityService(branchRepository, tokenRepository, counterRepository);
        branch = new Branch();
        branch.setId("branch-1");
        lenient().when(branchRepository.findById("branch-1")).thenReturn(Optional.of(branch));
    }

    private Counter counter(String id, String userId) {
        Counter c = new Counter();
        c.setId(id);
        c.setServiceIds(List.of("service-1"));
        c.setUserId(userId);
        c.setEnabled(true);
        c.setPaused(false);
        return c;
    }

    private void queue(long waiting, Double avgSecondsToday, Counter... counters) {
        when(tokenRepository.countByServiceIdAndStatus("service-1", TokenStatus.WAITING)).thenReturn(waiting);
        when(tokenRepository.countByServiceIdAndStatus("service-1", TokenStatus.HOLD)).thenReturn(0L);
        lenient().when(tokenRepository.getAvgServiceSecondsForService(eq("service-1"), any()))
                .thenReturn(avgSecondsToday);
        when(counterRepository.findByBranchId("branch-1")).thenReturn(List.of(counters));
    }

    @Test
    void noCloseTimeMeansNoAutomaticStop() {
        assertThatCode(() -> service.assertQueueFitsBeforeClosing("branch-1", "service-1", "en"))
                .doesNotThrowAnyException();
    }

    @Test
    void estimatesFromWaitingCountAverageTimeAndStaffedCounters() {
        // 11 people (10 waiting + new one) x 6 min / 2 staffed counters = 33 min
        queue(10, 360.0, counter("c1", "u1"), counter("c2", "u2"), counter("c3", null));

        assertThat(service.estimateFinish("branch-1", "service-1", LocalTime.of(9, 0)))
                .isEqualTo(LocalTime.of(9, 33));
    }

    @Test
    void usesTheDefaultTimeWhenTheServiceHasNoHistory() {
        queue(2, null, counter("c1", "u1"));

        assertThat(service.estimateFinish("branch-1", "service-1", LocalTime.of(9, 0)))
                .isEqualTo(LocalTime.of(9, 0).plusMinutes(3L * TicketCapacityService.DEFAULT_SERVICE_MINUTES));
    }

    @Test
    void refusesWhenTheQueueRunsPastClosing() {
        // Closing time already passed today, so any queue runs past it
        branch.setOfficeCloseTime(LocalTime.MIN);
        queue(0, 60.0, counter("c1", "u1"));

        assertThatThrownBy(() -> service.assertQueueFitsBeforeClosing("branch-1", "service-1", "ar"))
                .isInstanceOf(TicketIssuingClosedException.class)
                .hasMessageContaining("قبل موعد الإغلاق");
    }
}
