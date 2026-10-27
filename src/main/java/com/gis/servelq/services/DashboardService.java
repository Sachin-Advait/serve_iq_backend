package com.gis.servelq.services;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.Token;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final double SLA_RATE_TARGET = 65.0;

    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final ServiceSlaResolver serviceSlaResolver;

    /**
     * Get complete dashboard overview
     */
    public DashboardOverviewDTO getDashboardOverview(String branchId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        return DashboardOverviewDTO.builder()
                .kpis(getKpis(branchId, date))
                .visitorFlow(getVisitorFlow(branchId, date))
                .serviceTrends(getServiceTrends(branchId, date))
                .build();
    }

    /**
     * Calculate KPI metrics
     */
    public List<KpiDTO> getKpis(String branchId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = date.minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = date.atStartOfDay();

        // 1. Visitors Today
        long todayVisitors = tokenRepository.countByBranchIdAndCreatedAtBetween(
                branchId, startOfDay, endOfDay);
        long yesterdayVisitors = tokenRepository.countByBranchIdAndCreatedAtBetween(
                branchId, yesterdayStart, yesterdayEnd);
        String visitorChange = calculatePercentageChange(todayVisitors, yesterdayVisitors);

        // 2. Average Service Time
        double avgServiceTime = calculateAverageServiceTime(branchId, startOfDay, endOfDay);
        double yesterdayAvgService = calculateAverageServiceTime(branchId, yesterdayStart, yesterdayEnd);
        String serviceChange = formatTimeChange(avgServiceTime, yesterdayAvgService);

        // 3. SLA Compliance: on-time / judged tickets; tickets still inside their limit are pending
        Map<String, Integer> sla = serviceSlaResolver.forBranch(branchId);
        List<Token> todayTokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                branchId, startOfDay, endOfDay);
        LocalDateTime asOf = ServiceSlaResolver.asOf(endOfDay);
        ServiceSlaResolver.Tally waitingTally = ServiceSlaResolver.waitingTally(todayTokens, sla, asOf);

        // 3b. Serving-time SLA, judged independently of waiting
        ServiceSlaResolver.Tally servingTally = ServiceSlaResolver.servingTally(todayTokens,
                serviceSlaResolver.servingForBranch(branchId), asOf);

        // 4. Active Counters: an agent is logged in and the counter is not on a break
        List<Counter> counters = counterRepository.findByBranchId(branchId);
        long totalCounters = counters.stream().filter(c -> Boolean.TRUE.equals(c.getEnabled())).count();
        long activeCounters = counters.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()) && c.getUserId() != null
                        && !Boolean.TRUE.equals(c.getPaused()))
                .count();
        double utilizationRate = totalCounters == 0 ? 0 :
                (activeCounters * 100.0) / totalCounters;

        List<KpiDTO> kpis = new ArrayList<>();

        kpis.add(KpiDTO.builder()
                .icon("USERS")
                .label("VISITORS TODAY")
                .value(String.valueOf(todayVisitors))
                .change(visitorChange)
                .positive(todayVisitors >= yesterdayVisitors)
                .build());

        kpis.add(KpiDTO.builder()
                .icon("CLOCK")
                .label("AVG SERVICE TIME")
                .value(String.format("%.1fmin", avgServiceTime))
                .change(serviceChange)
                .positive(avgServiceTime <= yesterdayAvgService)
                .build());

        kpis.add(slaKpi("TRENDING_UP", "SLA COMPLIANCE", waitingTally));
        kpis.add(slaKpi("CLOCK", "SERVING SLA", servingTally));

        kpis.add(KpiDTO.builder()
                .icon("BAR_CHART")
                .label("ACTIVE COUNTERS")
                .value(String.format("%d/%d", activeCounters, totalCounters))
                .change(String.format("%.0f%% utilization", utilizationRate))
                .neutral(true)
                .build());

        return kpis;
    }

    /**
     * Get hourly visitor flow data
     */
    public List<VisitorFlowDTO> getVisitorFlow(String branchId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        Map<Integer, List<Token>> byHour = tokensByHour(branchId, date);

        List<VisitorFlowDTO> flowData = new ArrayList<>();
        for (int hour : chartHours(byHour)) {
            flowData.add(VisitorFlowDTO.builder()
                    .hour(String.format("%02d:00", hour))
                    .visitors(byHour.getOrDefault(hour, List.of()).size())
                    .build());
        }
        return flowData;
    }

    /**
     * Get service time trends (wait time vs service time)
     */
    public List<ServiceTrendDTO> getServiceTrends(String branchId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        Map<Integer, List<Token>> byHour = tokensByHour(branchId, date);

        List<ServiceTrendDTO> trends = new ArrayList<>();
        for (int hour : chartHours(byHour)) {
            List<Token> tokens = byHour.getOrDefault(hour, List.of()).stream()
                    .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                    .toList();

            double avgWaitTime = tokens.stream()
                    // Waiting ends at the first call, as in the SLA reports
                    .mapToDouble(t -> minutesBetween(t.getCreatedAt(),
                            t.getFirstCalledAt() != null ? t.getFirstCalledAt() : t.getStartAt()))
                    .average()
                    .orElse(0.0);

            double avgServiceTime = tokens.stream()
                    .mapToDouble(t -> minutesBetween(t.getStartAt(), t.getEndAt()))
                    .average()
                    .orElse(0.0);

            trends.add(ServiceTrendDTO.builder()
                    .time(String.format("%02d:00", hour))
                    .wait(avgWaitTime)
                    .service(avgServiceTime)
                    .build());
        }
        return trends;
    }

    /**
     * The day's tickets grouped by the hour they were issued. One query for the
     * whole day; each hour is [h:00, h+1:00), so no ticket falls between hours.
     */
    private Map<Integer, List<Token>> tokensByHour(String branchId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return tokenRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end).stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(end))
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().getHour()));
    }

    /** 08:00 to 18:00, stretched to take in any hour that has tickets. */
    private static List<Integer> chartHours(Map<Integer, List<Token>> byHour) {
        int first = Math.min(8, byHour.keySet().stream().min(Integer::compare).orElse(8));
        int last = Math.max(18, byHour.keySet().stream().max(Integer::compare).orElse(18));
        List<Integer> hours = new ArrayList<>();
        for (int h = first; h <= last; h++) hours.add(h);
        return hours;
    }

    /**
     * Calculate average service time for tokens in a time range
     */
    private double calculateAverageServiceTime(String branchId, LocalDateTime start, LocalDateTime end) {
        return tokenRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end)
                .stream()
                .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                .mapToDouble(t -> minutesBetween(t.getStartAt(), t.getEndAt()))
                .average()
                .orElse(0.0);
    }

    /** Minutes with seconds kept, so a 40 second visit is 0.7 min and not 0. */
    private static double minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).getSeconds()) / 60.0;
    }

    /** SLA tile; with no ticket judged yet it shows a dash instead of a red 0%. */
    private static KpiDTO slaKpi(String icon, String label, ServiceSlaResolver.Tally tally) {
        if (tally.judged() == 0) {
            return KpiDTO.builder().icon(icon).label(label).value("-")
                    .change("No tickets judged yet").neutral(true).build();
        }
        double rate = tally.rate();
        return KpiDTO.builder()
                .icon(icon)
                .label(label)
                .value(String.format("%.0f%%", rate))
                .change(String.format("%+.0f%% from target", rate - SLA_RATE_TARGET))
                .positive(rate >= SLA_RATE_TARGET)
                .build();
    }

    /**
     * Calculate percentage change between two values
     */
    private String calculatePercentageChange(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? "+100% from yesterday" : "No change from yesterday";
        }
        double change = ((double)(current - previous) / previous) * 100;
        return String.format("%+.0f%% from yesterday", change);
    }

    /**
     * Format time change for display
     */
    private String formatTimeChange(double current, double previous) {
        double diff = current - previous;
        if (diff < 0) {
            return String.format("%.1fm improvement", Math.abs(diff));
        } else if (diff > 0) {
            return String.format("+%.1fm from yesterday", diff);
        }
        return "No change";
    }
}