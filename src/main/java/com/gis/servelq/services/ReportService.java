package com.gis.servelq.services;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
import lombok.RequiredArgsConstructor;
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
public class ReportService {

    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    // ==================== EXISTING TOKEN REPORT METHODS ====================

    // Completed tokens (serving report) with optional filters
    public List<TokenResponseDTO> getServingReport(String branchId, String counterId, String serviceId, LocalDate date) {
        return tokenRepository.findByBranchId(branchId)
                .stream()
                .filter(t -> t.getStatus() == TokenStatus.DONE)
                .filter(t -> counterId == null || counterId.isEmpty() || counterId.equals(t.getAssignedCounterId()))
                .filter(t -> serviceId == null || serviceId.isEmpty() || serviceId.equals(t.getServiceId()))
                .filter(t -> date == null || isSameDate(t.getCreatedAt(), date))
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    // Waiting tokens with optional filters
    public List<TokenResponseDTO> getWaitingReport(String branchId, String counterId, String serviceId, LocalDate date) {
        return tokenRepository.findByBranchId(branchId)
                .stream()
                .filter(t -> t.getStatus() == TokenStatus.WAITING)
                .filter(t -> counterId == null || counterId.isEmpty() || counterId.equals(t.getAssignedCounterId()))
                .filter(t -> serviceId == null || serviceId.isEmpty() || serviceId.equals(t.getServiceId()))
                .filter(t -> date == null || isSameDate(t.getCreatedAt(), date))
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    // Transferred tokens report
    public List<TokenResponseDTO> getTransferredTokensReport(String branchId, String counterId, String serviceId, LocalDate date) {
        return tokenRepository.findByBranchId(branchId)
                .stream()
                .filter(Token::getIsTransfer)
                .filter(t -> counterId == null || counterId.isEmpty() || counterId.equals(t.getAssignedCounterId()))
                .filter(t -> serviceId == null || serviceId.isEmpty() || serviceId.equals(t.getServiceId()))
                .filter(t -> date == null || isSameDate(t.getCreatedAt(), date))
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    // Helper method to check if token's createdAt is on the same date
    private boolean isSameDate(LocalDateTime tokenTime, LocalDate filterDate) {
        if (tokenTime == null) return false;
        LocalDate tokenDate = tokenTime.toLocalDate();
        return tokenDate.equals(filterDate);
    }

    // ==================== NEW DASHBOARD ANALYTICS METHODS ====================

    /**
     * Get available reports overview
     */
    public List<ReportOverviewDTO> getReportsOverview(String branchId) {
        List<ReportOverviewDTO> reports = new ArrayList<>();

        long tokenCount = tokenRepository.countByBranchId(branchId);
        boolean hasData = tokenCount > 0;

        reports.add(ReportOverviewDTO.builder()
                .reportType("DAILY_SUMMARY")
                .title("Daily Summary")
                .description("Complete daily operations report")
                .icon("FileText")
                .color("#5b50e8")
                .hasData(hasData)
                .build());

        reports.add(ReportOverviewDTO.builder()
                .reportType("SERVICE_ANALYTICS")
                .title("Service Analytics")
                .description("Service-level performance metrics")
                .icon("Activity")
                .color("#00c48c")
                .hasData(hasData)
                .build());

        reports.add(ReportOverviewDTO.builder()
                .reportType("SLA_COMPLIANCE")
                .title("SLA Compliance")
                .description("Service level agreement tracking")
                .icon("Gauge")
                .color("#ffa26b")
                .hasData(hasData)
                .build());

        reports.add(ReportOverviewDTO.builder()
                .reportType("COUNTER_UTILIZATION")
                .title("Counter Utilization")
                .description("Counter efficiency analysis")
                .icon("Users")
                .color("#8b5cf6")
                .hasData(hasData)
                .build());

        return reports;
    }

    /**
     * Generate daily summary report
     */
    public DailySummaryDTO getDailySummary(String branchId, LocalDate date) {
        if (date == null) date = LocalDate.now();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        List<Token> tokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                branchId, startOfDay, endOfDay);

        long totalGenerated = tokens.size();
        long tokensServed = tokens.stream().filter(t -> t.getStatus() == TokenStatus.DONE).count();
        long tokensWaiting = tokens.stream().filter(t -> t.getStatus() == TokenStatus.WAITING).count();
        long tokensNoShow = tokens.stream().filter(t -> t.getStatus() == TokenStatus.NO_SHOW).count();
        long tokensTransferred = tokens.stream().filter(Token::getIsTransfer).count();

        double avgWaitTime = calculateAverageWaitTime(tokens);
        double avgServiceTime = calculateAverageServiceTime(tokens);
        double slaRate = calculateSlaRate(tokens, 15);

        // Find peak hour
        Map<String, Long> hourlyTokens = tokens.stream()
                .filter(t -> t.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:00")),
                        Collectors.counting()
                ));

        String peakHour = hourlyTokens.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        long peakHourTokens = hourlyTokens.getOrDefault(peakHour, 0L);

        // Tokens by service
        Map<String, Long> tokensByService = tokens.stream()
                .collect(Collectors.groupingBy(
                        Token::getServiceName,
                        Collectors.counting()
                ));

        // Tokens by status
        Map<String, Long> tokensByStatus = tokens.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStatus().name(),
                        Collectors.counting()
                ));

        return DailySummaryDTO.builder()
                .date(date)
                .branchName(branch.getName())
                .totalTokensGenerated(totalGenerated)
                .tokensServed(tokensServed)
                .tokensWaiting(tokensWaiting)
                .tokensNoShow(tokensNoShow)
                .tokensTransferred(tokensTransferred)
                .averageWaitTimeMinutes(avgWaitTime)
                .averageServiceTimeMinutes(avgServiceTime)
                .slaComplianceRate(slaRate)
                .peakHour(peakHour)
                .peakHourTokens(peakHourTokens)
                .tokensByService(tokensByService)
                .tokensByStatus(tokensByStatus)
                .build();
    }

    /**
     * Generate service analytics report
     */
    public ServiceAnalyticsDTO getServiceAnalytics(String branchId, LocalDate date) {
        if (date == null) date = LocalDate.now();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        List<Services> services = serviceRepository.findByBranchId(branchId);
        List<Token> allTokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                branchId, startOfDay, endOfDay);

        List<ServiceMetricDTO> serviceMetrics = new ArrayList<>();

        for (Services service : services) {
            List<Token> serviceTokens = allTokens.stream()
                    .filter(t -> t.getServiceId().equals(service.getId()))
                    .toList();

            if (serviceTokens.isEmpty()) continue;

            long totalTokens = serviceTokens.size();
            long completedTokens = serviceTokens.stream()
                    .filter(t -> t.getStatus() == TokenStatus.DONE).count();

            double avgServiceTime = calculateAverageServiceTime(serviceTokens);
            double avgWaitTime = calculateAverageWaitTime(serviceTokens);
            double completionRate = totalTokens > 0 ? (completedTokens * 100.0 / totalTokens) : 0;

            serviceMetrics.add(ServiceMetricDTO.builder()
                    .serviceId(service.getId())
                    .serviceName(service.getName())
                    .totalTokens(totalTokens)
                    .completedTokens(completedTokens)
                    .averageServiceTime(avgServiceTime)
                    .averageWaitTime(avgWaitTime)
                    .completionRate(completionRate)
                    .build());
        }

        long totalTokens = allTokens.size();
        long totalCompleted = allTokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.DONE).count();

        TotalServiceStatsDTO totals = TotalServiceStatsDTO.builder()
                .totalTokens(totalTokens)
                .totalCompleted(totalCompleted)
                .overallAverageServiceTime(calculateAverageServiceTime(allTokens))
                .overallCompletionRate(totalTokens > 0 ? (totalCompleted * 100.0 / totalTokens) : 0)
                .build();

        return ServiceAnalyticsDTO.builder()
                .branchName(branch.getName())
                .services(serviceMetrics)
                .totals(totals)
                .build();
    }

    /**
     * Generate SLA compliance report
     */
    public SlaComplianceDTO getSlaCompliance(String branchId, LocalDate date) {
        if (date == null) date = LocalDate.now();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        int slaTargetMinutes = 15;

        List<Token> tokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                        branchId, startOfDay, endOfDay)
                .stream()
                .filter(t -> t.getCreatedAt() != null)
                .toList();

        long totalTokens = tokens.size();
        long slaCompliant = tokens.stream()
                .filter(t -> t.getStartAt() != null)
                .filter(t -> Duration.between(t.getCreatedAt(), t.getStartAt()).toMinutes() <= slaTargetMinutes)
                .count();
        long slaBreached = totalTokens - slaCompliant;

        double overallSlaRate = totalTokens > 0 ? (slaCompliant * 100.0 / totalTokens) : 0;

        // Hourly breakdown
        List<HourlySlaDTO> hourlyBreakdown = new ArrayList<>();
        for (int hour = 8; hour <= 18; hour++) {
            LocalDateTime hourStart = date.atTime(LocalTime.of(hour, 0));
            LocalDateTime hourEnd = date.atTime(LocalTime.of(hour, 59, 59));

            List<Token> hourTokens = tokens.stream()
                    .filter(t -> !t.getCreatedAt().isBefore(hourStart) && !t.getCreatedAt().isAfter(hourEnd))
                    .toList();

            long hourTotal = hourTokens.size();
            long hourCompliant = hourTokens.stream()
                    .filter(t -> t.getStartAt() != null)
                    .filter(t -> Duration.between(t.getCreatedAt(), t.getStartAt()).toMinutes() <= slaTargetMinutes)
                    .count();

            double hourRate = hourTotal > 0 ? (hourCompliant * 100.0 / hourTotal) : 0;

            hourlyBreakdown.add(HourlySlaDTO.builder()
                    .hour(String.format("%02d:00", hour))
                    .totalTokens(hourTotal)
                    .compliantTokens(hourCompliant)
                    .complianceRate(hourRate)
                    .build());
        }

        return SlaComplianceDTO.builder()
                .branchName(branch.getName())
                .overallSlaRate(overallSlaRate)
                .totalTokens(totalTokens)
                .slaCompliantTokens(slaCompliant)
                .slaBreachedTokens(slaBreached)
                .slaTargetMinutes(slaTargetMinutes)
                .hourlyBreakdown(hourlyBreakdown)
                .build();
    }

    /**
     * Generate counter utilization report
     */
    public CounterUtilizationDTO getCounterUtilization(String branchId, LocalDate date) {
        if (date == null) date = LocalDate.now();

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        List<Counter> counters = counterRepository.findByBranchId(branchId);
        List<Token> allTokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                branchId, startOfDay, endOfDay);

        int totalCounters = counters.size();
        int activeCounters = (int) counters.stream().filter(c -> c.getEnabled() && !c.getPaused()).count();
        int idleCounters = (int) counters.stream().filter(c -> c.getEnabled() && c.getPaused()).count();
        int pausedCounters = (int) counters.stream().filter(c -> !c.getEnabled()).count();

        double overallUtilization = totalCounters > 0 ? (activeCounters * 100.0 / totalCounters) : 0;

        List<CounterUtilizationDetailDTO> counterDetails = new ArrayList<>();

        for (Counter counter : counters) {
            List<Token> counterTokens = allTokens.stream()
                    .filter(t -> counter.getId().equals(t.getAssignedCounterId()))
                    .toList();

            long tokensServed = counterTokens.stream()
                    .filter(t -> t.getStatus() == TokenStatus.DONE).count();

            double avgServiceTime = calculateAverageServiceTime(counterTokens);

            long totalServiceSeconds = counterTokens.stream()
                    .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                    .mapToLong(t -> Duration.between(t.getStartAt(), t.getEndAt()).getSeconds())
                    .sum();

            double utilizationRate = (totalServiceSeconds * 100.0) / 28800; // 8 hours = 28800 seconds
            utilizationRate = Math.min(utilizationRate, 100.0);

            String agentName = "Unassigned";
            if (counter.getUserId() != null) {
                agentName = userRepository.findById(counter.getUserId())
                        .map(User::getName)
                        .orElse("Unknown");
            }

            String status = counter.getEnabled() ?
                    (counter.getPaused() ? "Paused" : "Active") : "Disabled";

            counterDetails.add(CounterUtilizationDetailDTO.builder()
                    .counterId(counter.getId())
                    .counterCode(counter.getCode())
                    .counterName(counter.getName())
                    .agentName(agentName)
                    .tokensServed(tokensServed)
                    .averageServiceTime(avgServiceTime)
                    .utilizationRate(utilizationRate)
                    .status(status)
                    .build());
        }

        return CounterUtilizationDTO.builder()
                .branchName(branch.getName())
                .totalCounters(totalCounters)
                .activeCounters(activeCounters)
                .idleCounters(idleCounters)
                .pausedCounters(pausedCounters)
                .overallUtilizationRate(overallUtilization)
                .counters(counterDetails)
                .build();
    }

    // ==================== HELPER METHODS ====================

    private double calculateAverageWaitTime(List<Token> tokens) {
        return tokens.stream()
                .filter(t -> t.getStartAt() != null && t.getCreatedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getStartAt()).toMinutes())
                .average()
                .orElse(0.0);
    }

    private double calculateAverageServiceTime(List<Token> tokens) {
        return tokens.stream()
                .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                .mapToLong(t -> Duration.between(t.getStartAt(), t.getEndAt()).toMinutes())
                .average()
                .orElse(0.0);
    }

    private double calculateSlaRate(List<Token> tokens, int targetMinutes) {
        long total = tokens.stream()
                .filter(t -> t.getCreatedAt() != null && t.getStartAt() != null)
                .count();

        if (total == 0) return 0;

        long compliant = tokens.stream()
                .filter(t -> t.getCreatedAt() != null && t.getStartAt() != null)
                .filter(t -> Duration.between(t.getCreatedAt(), t.getStartAt()).toMinutes() <= targetMinutes)
                .count();

        return (compliant * 100.0) / total;
    }
}