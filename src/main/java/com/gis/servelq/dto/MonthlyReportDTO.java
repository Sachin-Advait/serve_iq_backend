package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Monthly report (queue procedure section 14): the month's results, where
 * they missed their targets and what to do about it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportDTO {
    private String month;              // yyyy-MM
    private String branchName;

    private int slaTargetMinutes;
    private double slaRateTarget;
    private double noShowRateLimit;
    private double transferRateLimit;

    private long totalTokens;
    private long tokensServed;
    private long tokensNoShow;
    private long tokensTransferred;
    private long tokensUnserved;
    private double noShowRate;
    private double transferRate;
    private double averageWaitTimeMinutes;
    private double averageServiceTimeMinutes;
    private double slaComplianceRate;
    private int workingDays;
    private double averageTokensPerDay;
    private String busiestDay;
    private long busiestDayTokens;
    private String peakHour;

    private List<Day> days;
    private List<ServiceRow> services;
    private List<CounterRow> counters;
    private List<ReasonCount> transferReasons;
    private List<Finding> findings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day {
        private LocalDate date;
        private long totalTokens;
        private long served;
        private long noShow;
        private long transferred;
        private double averageWaitTimeMinutes;
        private double averageServiceTimeMinutes;
        private double slaComplianceRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceRow {
        private String serviceName;
        private long totalTokens;
        private long served;
        private long noShow;
        private long transferred;
        private double averageWaitTimeMinutes;
        private double averageServiceTimeMinutes;
        private double slaComplianceRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterRow {
        private String counterName;
        private long served;
        private double averageServiceTimeMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonCount {
        private String reason;
        private String label;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {
        private boolean onTarget;
        private String indicator;
        private String target;
        private String actual;
        private String finding;
        private String suggestedAction;
    }
}
