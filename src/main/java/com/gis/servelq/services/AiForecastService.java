package com.gis.servelq.services;

import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.*;
import com.gis.servelq.repository.AiForecastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiForecastService {

    public static final String SYSTEM_CODE = "VISITOR_MANAGEMENT_SYSTEM";

    private final AiForecastRepository repo;

    @Value("${analytics.max-history:500}")
    private int maxHistory;

    @Value("${analytics.stale-hourly-minutes:120}")
    private long staleHourlyMinutes;

    @Value("${analytics.stale-daily-minutes:2160}")
    private long staleDailyMinutes;

    public AiDashboardDTO dashboard(String frequency) {
        String f = normalizeFrequency(frequency);
        List<AnalyticsRecordDTO> records = markStale(repo.findCurrent(f));
        return new AiDashboardDTO(SYSTEM_CODE, f, Instant.now(), records);
    }

    public List<AnalyticsRecordDTO> current(String frequency) {
        return markStale(repo.findCurrent(normalizeFrequency(frequency)));
    }

    public AnalyticsRecordDTO currentOne(String useCaseCode, String frequency) {
        if (useCaseCode == null || useCaseCode.isBlank()) {
            throw new IllegalArgumentException("useCaseCode is required");
        }
        String code = useCaseCode.trim().toUpperCase();
        return repo.findOneCurrent(code, normalizeFrequency(frequency))
                .map(this::markStale)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No current forecast for " + code));
    }

    public List<AnalyticsRecordDTO> history(String useCaseCode, String frequency,
                                            Instant from, Instant to, Integer limit) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        if (useCaseCode == null || useCaseCode.isBlank()) {
            throw new IllegalArgumentException("useCaseCode is required");
        }
        String code = useCaseCode.trim().toUpperCase();
        int n = limit == null ? 100 : Math.min(Math.max(limit, 1), maxHistory);
        return markStale(repo.findHistory(code, normalizeFrequency(frequency), from, to, n));
    }

    public DataHealthDTO health() {
        try {
            long count = repo.countCurrent();
            Instant latest = repo.latestGeneratedAt().orElse(null);
            return new DataHealthDTO(
                    SYSTEM_CODE, true, count, latest, count > 0,
                    count > 0 ? "AI forecast data is available."
                            : "Database reachable but no current AI forecast rows exist."
            );
        } catch (Exception e) {
            log.error("AI data-health check failed", e);
            return new DataHealthDTO(
                    SYSTEM_CODE, false, 0, null, false,
                    "AI database read failed or tables are missing."
            );
        }
    }

    // ---- helpers ----

    private String normalizeFrequency(String f) {
        if (f == null || f.isBlank()) return null;
        String x = f.trim().toUpperCase();
        if (!x.equals("DAILY") && !x.equals("HOURLY")) {
            throw new IllegalArgumentException("frequency must be DAILY or HOURLY");
        }
        return x;
    }

    private List<AnalyticsRecordDTO> markStale(List<AnalyticsRecordDTO> rows) {
        rows.forEach(this::markStale);
        return rows;
    }

    private AnalyticsRecordDTO markStale(AnalyticsRecordDTO r) {
        long max = "HOURLY".equals(r.getFrequency()) ? staleHourlyMinutes : staleDailyMinutes;
        boolean stale = r.getGeneratedAt() == null
                || Duration.between(r.getGeneratedAt(), Instant.now()).toMinutes() > max;
        r.setStale(stale);
        return r;
    }
}