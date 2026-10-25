package com.gis.servelq.services;

import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterBreak;
import com.gis.servelq.repository.CounterBreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Keeps counter_breaks in step with Counter.paused: a pause opens a break and
 * a resume or release closes it. Call after every change to paused.
 */
@Service
@RequiredArgsConstructor
public class CounterBreakService {

    private final CounterBreakRepository counterBreakRepository;

    public void sync(Counter counter) {
        if (counter == null || counter.getId() == null) return;
        Optional<CounterBreak> open = counterBreakRepository.findFirstByCounterIdAndEndedAtIsNull(counter.getId());
        boolean paused = Boolean.TRUE.equals(counter.getPaused());

        if (paused && open.isEmpty()) {
            CounterBreak b = new CounterBreak();
            b.setCounterId(counter.getId());
            b.setUserId(counter.getUserId());
            b.setBranchId(counter.getBranchId());
            b.setStartedAt(LocalDateTime.now());
            counterBreakRepository.save(b);
        } else if (!paused && open.isPresent()) {
            CounterBreak b = open.get();
            b.setEndedAt(LocalDateTime.now());
            counterBreakRepository.save(b);
        }
    }
}
