package com.gis.servelq.services;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
                .aiRecommendations(getAiRecommendations(branchId))
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

        List<VisitorFlowDTO> flowData = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int hour = 8; hour <= 18; hour++) {
            LocalDateTime hourStart = date.atTime(LocalTime.of(hour, 0));
            LocalDateTime hourEnd = date.atTime(LocalTime.of(hour, 59, 59));

            long visitorCount = tokenRepository.countByBranchIdAndCreatedAtBetween(
                    branchId, hourStart, hourEnd);

            flowData.add(VisitorFlowDTO.builder()
                    .hour(hourStart.format(formatter))
                    .visitors((int) visitorCount)
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

        List<ServiceTrendDTO> trends = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (int hour = 8; hour <= 18; hour++) {
            LocalDateTime hourStart = date.atTime(LocalTime.of(hour, 0));
            LocalDateTime hourEnd = date.atTime(LocalTime.of(hour, 59, 59));

            List<Token> tokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                            branchId, hourStart, hourEnd)
                    .stream()
                    .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                    .toList();

            double avgWaitTime = tokens.stream()
                    .filter(t -> t.getCreatedAt() != null)
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
                    .time(hourStart.format(formatter))
                    .wait(avgWaitTime)
                    .service(avgServiceTime)
                    .build());
        }

        return trends;
    }

    /**
     * Generate AI recommendations based on current queue status
     */
    public List<AiRecommendationDTO> getAiRecommendations(String branchId) {
        List<AiRecommendationDTO> recommendations = new ArrayList<>();
        List<Counter> counters = counterRepository.findByBranchId(branchId);

        for (Counter counter : counters) {
            if (!counter.getEnabled()) continue;

            // Get waiting tokens for this counter's service
            long waitingTokens = CounterServiceLinker.serviceIdsOf(counter).stream()
                    .mapToLong(id -> tokenRepository.countByServiceIdAndStatus(id, TokenStatus.WAITING))
                    .sum();

            // Get current serving tokens
            long servingTokens = tokenRepository
                    .countByAssignedCounterIdAndStatus(counter.getId(), TokenStatus.SERVING);

            String action;
            String reason;
            String impact;
            int confidence;

            if (waitingTokens > 10 && servingTokens == 0) {
                action = "Open";
                reason = "High queue backlog detected";
                impact = "↓ wait time ~" + (waitingTokens * 2) + "%";
                confidence = 85 + (int)(Math.random() * 10);
            } else if (waitingTokens == 0 && servingTokens == 0) {
                action = "Pause";
                reason = "No traffic for this counter";
                impact = "---";
                confidence = 90 + (int)(Math.random() * 5);
            } else if (waitingTokens > 5 && servingTokens > 0) {
                action = "Stay Active";
                reason = "Steady demand, continue serving";
                impact = "Maintain current flow";
                confidence = 80 + (int)(Math.random() * 15);
            } else {
                action = "Monitor";
                reason = "Low activity, monitor queue";
                impact = "---";
                confidence = 70 + (int)(Math.random() * 20);
            }

            // Only include actionable recommendations
            if (!action.equals("Monitor") || waitingTokens > 3) {
                recommendations.add(AiRecommendationDTO.builder()
                        .counter(counter.getCode())
                        .action(action)
                        .reason(reason)
                        .impact(impact)
                        .confidence(Math.min(confidence, 98))
                        .build());
            }
        }

        // Sort by confidence descending
        recommendations.sort((a, b) -> Integer.compare(b.getConfidence(), a.getConfidence()));

        // Limit to top 5 recommendations
        return recommendations.stream().limit(5).collect(Collectors.toList());
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