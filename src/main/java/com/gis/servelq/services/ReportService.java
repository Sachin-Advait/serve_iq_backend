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
import java.time.YearMonth;
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
    private final TransferReasonService transferReasonService;
    private final ServiceSlaResolver serviceSlaResolver;

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
        double slaRate = calculateSlaRate(tokens, serviceSlaResolver.forBranch(branchId));

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

        Map<String, Integer> sla = serviceSlaResolver.forBranch(branchId);

        List<Token> tokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                        branchId, startOfDay, endOfDay)
                .stream()
                .filter(t -> t.getCreatedAt() != null)
                .toList();

        long totalTokens = tokens.size();
        long slaCompliant = tokens.stream()
                .filter(t -> ServiceSlaResolver.withinSla(t, sla))
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
                    .filter(t -> ServiceSlaResolver.withinSla(t, sla))
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
                .slaTargetMinutes(ServiceSlaResolver.DEFAULT_SLA_SECONDS / 60)
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

    // ==================== MONTHLY REPORT ====================

    // Defaults until the Office of the Assistant CEO approves official
    // indicators. 65% matches the colour threshold the admin panel uses.
    private static final double SLA_RATE_TARGET = 65.0;
    private static final double NO_SHOW_RATE_LIMIT = 10.0;
    private static final double TRANSFER_RATE_LIMIT = 10.0;

    private static boolean isServed(Token t) {
        return t.getStatus() == TokenStatus.DONE || t.getStatus() == TokenStatus.REVIEW;
    }

    private static boolean isUnserved(Token t) {
        return t.getStatus() == TokenStatus.WAITING
                || t.getStatus() == TokenStatus.HOLD
                || t.getStatus() == TokenStatus.CALLING;
    }

    private static double rate(long part, long whole) {
        return whole > 0 ? part * 100.0 / whole : 0;
    }

    /**
     * Monthly report: totals, a day-by-day and per-service breakdown, transfer
     * reasons, and findings that compare the month against its targets with a
     * suggested corrective action for each miss.
     */
    public MonthlyReportDTO getMonthlyReport(String branchId, YearMonth month) {
        if (month == null) month = YearMonth.now();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        List<Token> tokens = tokenRepository.findByBranchIdAndCreatedAtBetween(
                        branchId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay())
                .stream()
                .filter(t -> t.getCreatedAt() != null)
                .toList();
        Map<String, Integer> sla = serviceSlaResolver.forBranch(branchId);

        long total = tokens.size();
        long served = tokens.stream().filter(ReportService::isServed).count();
        long noShow = tokens.stream().filter(t -> t.getStatus() == TokenStatus.NO_SHOW).count();
        long transferred = tokens.stream().filter(t -> Boolean.TRUE.equals(t.getIsTransfer())).count();
        long unserved = tokens.stream().filter(ReportService::isUnserved).count();

        // Day by day
        Map<LocalDate, List<Token>> byDay = tokens.stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate(), TreeMap::new, Collectors.toList()));
        List<MonthlyReportDTO.Day> days = byDay.entrySet().stream()
                .map(e -> MonthlyReportDTO.Day.builder()
                        .date(e.getKey())
                        .totalTokens(e.getValue().size())
                        .served(e.getValue().stream().filter(ReportService::isServed).count())
                        .noShow(e.getValue().stream().filter(t -> t.getStatus() == TokenStatus.NO_SHOW).count())
                        .transferred(e.getValue().stream().filter(t -> Boolean.TRUE.equals(t.getIsTransfer())).count())
                        .averageWaitTimeMinutes(calculateAverageWaitTime(e.getValue()))
                        .averageServiceTimeMinutes(calculateAverageServiceTime(e.getValue()))
                        .slaComplianceRate(calculateSlaRate(e.getValue(), sla))
                        .build())
                .toList();

        // Per service
        List<MonthlyReportDTO.ServiceRow> services = tokens.stream()
                .collect(Collectors.groupingBy(Token::getServiceName, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> MonthlyReportDTO.ServiceRow.builder()
                        .serviceName(e.getKey())
                        .slaMinutes(ServiceSlaResolver.slaSeconds(e.getValue().get(0), sla) / 60.0)
                        .totalTokens(e.getValue().size())
                        .served(e.getValue().stream().filter(ReportService::isServed).count())
                        .noShow(e.getValue().stream().filter(t -> t.getStatus() == TokenStatus.NO_SHOW).count())
                        .transferred(e.getValue().stream().filter(t -> Boolean.TRUE.equals(t.getIsTransfer())).count())
                        .averageWaitTimeMinutes(calculateAverageWaitTime(e.getValue()))
                        .averageServiceTimeMinutes(calculateAverageServiceTime(e.getValue()))
                        .slaComplianceRate(calculateSlaRate(e.getValue(), sla))
                        .build())
                .sorted(Comparator.comparingLong(MonthlyReportDTO.ServiceRow::getTotalTokens).reversed())
                .toList();

        // Per counter (where the visitor was last served)
        List<MonthlyReportDTO.CounterRow> counters = tokens.stream()
                .filter(ReportService::isServed)
                .filter(t -> t.getAssignedCounterName() != null)
                .collect(Collectors.groupingBy(Token::getAssignedCounterName, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> MonthlyReportDTO.CounterRow.builder()
                        .counterName(e.getKey())
                        .served(e.getValue().size())
                        .averageServiceTimeMinutes(calculateAverageServiceTime(e.getValue()))
                        .build())
                .toList();

        // Transfer reasons
        List<MonthlyReportDTO.ReasonCount> reasons = tokens.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsTransfer()))
                .collect(Collectors.groupingBy(
                        t -> t.getTransferReason() != null ? t.getTransferReason() : "NOT_RECORDED",
                        Collectors.counting()))
                .entrySet().stream()
                .map(e -> MonthlyReportDTO.ReasonCount.builder()
                        .reason(e.getKey())
                        .label(reasonLabel(e.getKey()))
                        .count(e.getValue())
                        .build())
                .sorted(Comparator.comparingLong(MonthlyReportDTO.ReasonCount::getCount).reversed())
                .toList();

        String peakHour = tokens.stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:00")),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        MonthlyReportDTO.Day busiest = days.stream()
                .max(Comparator.comparingLong(MonthlyReportDTO.Day::getTotalTokens))
                .orElse(null);

        double slaRate = calculateSlaRate(tokens, sla);
        double noShowRate = rate(noShow, total);
        double transferRate = rate(transferred, total);

        List<MonthlyReportDTO.Finding> findings = buildFindings(
                total, slaRate, noShowRate, transferRate, unserved, services, reasons, peakHour);

        return MonthlyReportDTO.builder()
                .month(month.toString())
                .branchName(branch.getName())
                .slaTargetMinutes(ServiceSlaResolver.DEFAULT_SLA_SECONDS / 60)
                .slaRateTarget(SLA_RATE_TARGET)
                .noShowRateLimit(NO_SHOW_RATE_LIMIT)
                .transferRateLimit(TRANSFER_RATE_LIMIT)
                .totalTokens(total)
                .tokensServed(served)
                .tokensNoShow(noShow)
                .tokensTransferred(transferred)
                .tokensUnserved(unserved)
                .noShowRate(noShowRate)
                .transferRate(transferRate)
                .averageWaitTimeMinutes(calculateAverageWaitTime(tokens))
                .averageServiceTimeMinutes(calculateAverageServiceTime(tokens))
                .slaComplianceRate(slaRate)
                .workingDays(days.size())
                .averageTokensPerDay(days.isEmpty() ? 0 : (double) total / days.size())
                .busiestDay(busiest != null ? busiest.getDate().toString() : "N/A")
                .busiestDayTokens(busiest != null ? busiest.getTotalTokens() : 0)
                .peakHour(peakHour)
                .days(days)
                .services(services)
                .counters(counters)
                .transferReasons(reasons)
                .findings(findings)
                .build();
    }

    private String reasonLabel(String code) {
        if ("NOT_RECORDED".equals(code)) return "Not recorded";
        return transferReasonService.findByCode(code).map(TransferReason::getTitleEn).orElse(code);
    }

    private List<MonthlyReportDTO.Finding> buildFindings(long total, double slaRate, double noShowRate,
                                                         double transferRate, long unserved,
                                                         List<MonthlyReportDTO.ServiceRow> services,
                                                         List<MonthlyReportDTO.ReasonCount> reasons,
                                                         String peakHour) {
        List<MonthlyReportDTO.Finding> findings = new ArrayList<>();
        if (total == 0) {
            findings.add(MonthlyReportDTO.Finding.builder()
                    .onTarget(true)
                    .indicator("Activity")
                    .target("-")
                    .actual("0 tickets")
                    .finding("No tickets were issued this month.")
                    .suggestedAction("None.")
                    .build());
            return findings;
        }

        // SLA: wait time within target
        MonthlyReportDTO.ServiceRow worstService = services.stream()
                .filter(s -> s.getTotalTokens() > 0)
                .min(Comparator.comparingDouble(MonthlyReportDTO.ServiceRow::getSlaComplianceRate))
                .orElse(null);
        boolean slaOk = slaRate >= SLA_RATE_TARGET;
        findings.add(MonthlyReportDTO.Finding.builder()
                .onTarget(slaOk)
                .indicator("Visitors called within their service's SLA")
                .target(String.format("%.0f%% or more", SLA_RATE_TARGET))
                .actual(String.format("%.1f%%", slaRate))
                .finding(slaOk
                        ? "Waiting times met the target."
                        : "Waiting times missed the target"
                        + (worstService != null
                        ? String.format(", mostly in %s (%.1f%%)", worstService.getServiceName(),
                        worstService.getSlaComplianceRate())
                        : "")
                        + ". The busiest hour was " + peakHour + ".")
                .suggestedAction(slaOk
                        ? "Keep the current counter allocation."
                        : "Open more counters"
                        + (worstService != null ? " for " + worstService.getServiceName() : "")
                        + " around " + peakHour + ", and move staff from quieter services at that time.")
                .build());

        // No-shows
        boolean noShowOk = noShowRate <= NO_SHOW_RATE_LIMIT;
        findings.add(MonthlyReportDTO.Finding.builder()
                .onTarget(noShowOk)
                .indicator("No-show rate")
                .target(String.format("%.0f%% or less", NO_SHOW_RATE_LIMIT))
                .actual(String.format("%.1f%%", noShowRate))
                .finding(noShowOk
                        ? "No-shows stayed within the limit."
                        : "Too many visitors did not come when called.")
                .suggestedAction(noShowOk
                        ? "None."
                        : "Check that screen and audio announcements reach every waiting area, repeat calls "
                        + "before marking no-show, and remind visitors at reception to watch the screen.")
                .build());

        // Transfers
        boolean transferOk = transferRate <= TRANSFER_RATE_LIMIT;
        MonthlyReportDTO.ReasonCount topReason = reasons.isEmpty() ? null : reasons.get(0);
        findings.add(MonthlyReportDTO.Finding.builder()
                .onTarget(transferOk)
                .indicator("Transfer rate")
                .target(String.format("%.0f%% or less", TRANSFER_RATE_LIMIT))
                .actual(String.format("%.1f%%", transferRate))
                .finding(transferOk
                        ? "Few visitors had to be transferred."
                        : "Many visitors were transferred"
                        + (topReason != null ? ", most often because: " + topReason.getLabel().toLowerCase() : "")
                        + ".")
                .suggestedAction(transferOk ? "None." : transferAction(topReason))
                .build());

        // Tickets never served
        findings.add(MonthlyReportDTO.Finding.builder()
                .onTarget(unserved == 0)
                .indicator("Tickets not served")
                .target("0")
                .actual(String.valueOf(unserved))
                .finding(unserved == 0
                        ? "Every ticket was served, transferred or closed."
                        : unserved + " tickets were still waiting or on hold at the end of their day.")
                .suggestedAction(unserved == 0
                        ? "None."
                        : "Stop ticket issuing earlier on busy days when the waiting queue exceeds the "
                        + "remaining counter capacity, and direct visitors to online or Contact Centre channels.")
                .build());

        return findings;
    }

    private static String transferAction(MonthlyReportDTO.ReasonCount topReason) {
        if (topReason == null) return "Review why visitors are being transferred.";
        return switch (topReason.getReason()) {
            case "WRONG_SERVICE", "MISCLASSIFIED" ->
                    "Make kiosk service names clearer and have reception confirm the service before issuing a ticket.";
            case "MISSING_DOCUMENTS" ->
                    "Publish a required-documents checklist for each service at reception and online.";
            case "NEEDS_SPECIALIST" ->
                    "Add a specialist service stream so these visitors are routed to the specialist directly.";
            case "OTHER_SECTION" -> "Review the service streams so each visitor reaches the right section first.";
            case "ALTERNATIVE_CHANNEL" ->
                    "Tell visitors at reception which services are available online or through the Contact Centre.";
            case "CROWD_MANAGEMENT" -> "Review counter allocation for busy services.";
            default -> "Ask agents to record a specific transfer reason, then review the main causes.";
        };
    }

    // ==================== HELPER METHODS ====================

    private double calculateAverageWaitTime(List<Token> tokens) {
        return tokens.stream()
                .map(ServiceSlaResolver::waitSeconds)
                .filter(Objects::nonNull)
                .mapToDouble(sec -> sec / 60.0)
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

    private double calculateSlaRate(List<Token> tokens, Map<String, Integer> slaByService) {
        long total = tokens.stream()
                .filter(t -> ServiceSlaResolver.waitSeconds(t) != null)
                .count();

        if (total == 0) return 0;

        long compliant = tokens.stream()
                .filter(t -> ServiceSlaResolver.withinSla(t, slaByService))
                .count();

        return (compliant * 100.0) / total;
    }
}