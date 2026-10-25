package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.dto.TabularReportDTO;
import com.gis.servelq.dto.TabularReportDTO.Section;
import com.gis.servelq.dto.TabularReportDTO.Stat;
import com.gis.servelq.models.*;
import com.gis.servelq.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The five operational reports over a date range: agent performance, waiting
 * time and SLA, no-shows, service demand and peak hours, and agent utilization.
 */
@Service
@RequiredArgsConstructor
public class OperationsReportService {

    private static final int MAX_RANGE_DAYS = 92;
    // Same targets as the monthly report
    private static final double SLA_RATE_TARGET = 65.0;
    private static final double NO_SHOW_RATE_LIMIT = 10.0;

    private final TokenRepository tokenRepository;
    private final BranchRepository branchRepository;
    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final CounterBreakRepository counterBreakRepository;
    private final ServiceSlaResolver serviceSlaResolver;

    /** Branch, date range and the tokens issued in it. */
    private class Range {
        final Branch branch;
        final LocalDate from;
        final LocalDate to;
        final LocalDateTime start;
        final LocalDateTime end;
        final List<Token> tokens;

        Range(String branchId, LocalDate from, LocalDate to) {
            this.to = to != null ? to : LocalDate.now();
            this.from = from != null ? from : this.to;
            if (this.from.isAfter(this.to)) throw new BusinessException("'from' must not be after 'to'");
            if (ChronoUnit.DAYS.between(this.from, this.to) >= MAX_RANGE_DAYS) {
                throw new BusinessException("Pick a range of at most " + MAX_RANGE_DAYS + " days");
            }
            this.branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new BusinessException("Branch not found"));
            this.start = this.from.atStartOfDay();
            this.end = this.to.plusDays(1).atStartOfDay();
            this.tokens = tokenRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end).stream()
                    .filter(t -> t.getCreatedAt() != null)
                    .toList();
        }

        // Open sessions and breaks run until now, but not past the range
        LocalDateTime cap() {
            LocalDateTime now = LocalDateTime.now();
            return now.isBefore(end) ? now : end;
        }

        TabularReportDTO.TabularReportDTOBuilder report(String type, String title) {
            return TabularReportDTO.builder()
                    .reportType(type)
                    .title(title)
                    .branchName(branch.getName())
                    .from(from)
                    .to(to);
        }
    }

    // ==================== 1. AGENT PERFORMANCE ====================

    public TabularReportDTO agentPerformance(String branchId, LocalDate from, LocalDate to) {
        Range r = new Range(branchId, from, to);
        Map<String, Integer> sla = serviceSlaResolver.forBranch(branchId);
        List<Token> served = r.tokens.stream().filter(OperationsReportService::isServed).toList();

        Set<String> agentIds = new LinkedHashSet<>();
        served.forEach(t -> addIfSet(agentIds, t.getServedByUserId()));
        r.tokens.forEach(t -> addIfSet(agentIds, t.getNoShowUserId()));
        Map<String, String> names = userNames(agentIds);

        double branchAvgService = avgServiceMinutes(served);
        List<AgentRow> agents = agentIds.stream().map(id -> {
            List<Token> mine = served.stream().filter(t -> id.equals(t.getServedByUserId())).toList();
            long noShows = r.tokens.stream().filter(t -> id.equals(t.getNoShowUserId())).count();
            return new AgentRow(id, names.getOrDefault(id, "Unknown"), mine, noShows);
        }).sorted(Comparator.comparingInt((AgentRow a) -> a.served.size()).reversed()).toList();

        List<List<Object>> rows = agents.stream().map(a -> {
            double avg = avgServiceMinutes(a.served);
            return List.<Object>of(
                    a.name,
                    a.served.size(),
                    a.noShows,
                    round(avg),
                    round(totalServiceMinutes(a.served)),
                    round(avgWaitMinutes(a.served)),
                    round(slaRate(a.served, sla)),
                    round(pct(a.served.size(), served.size())),
                    a.served.isEmpty() || branchAvgService == 0 ? "-"
                            : String.format("%+.0f%%", (avg - branchAvgService) * 100 / branchAvgService));
        }).toList();

        // Served per day, one column per agent
        List<String> dayColumns = new ArrayList<>(List.of("Date"));
        agents.forEach(a -> dayColumns.add(a.name));
        List<List<Object>> dayRows = new ArrayList<>();
        for (LocalDate d = r.from; !d.isAfter(r.to); d = d.plusDays(1)) {
            LocalDate day = d;
            List<Object> row = new ArrayList<>(List.of(day.toString()));
            agents.forEach(a -> row.add(a.served.stream()
                    .filter(t -> t.getCreatedAt().toLocalDate().equals(day)).count()));
            dayRows.add(row);
        }

        List<Stat> summary = new ArrayList<>();
        summary.add(new Stat("Agents", String.valueOf(agents.size())));
        summary.add(new Stat("Tokens served", String.valueOf(served.size())));
        summary.add(new Stat("Average service time", minutes(branchAvgService)));
        agents.stream().filter(a -> !a.served.isEmpty()).findFirst()
                .ifPresent(a -> summary.add(new Stat("Most served", a.name + " (" + a.served.size() + ")")));
        agents.stream().filter(a -> !a.served.isEmpty())
                .min(Comparator.comparingDouble(a -> avgServiceMinutes(a.served)))
                .ifPresent(a -> summary.add(new Stat("Fastest average",
                        a.name + " (" + minutes(avgServiceMinutes(a.served)) + ")")));

        return r.report("AGENT_PERFORMANCE", "Agent Performance")
                .summary(summary)
                .sections(List.of(
                        new Section("Agent comparison", List.of("Agent", "Served", "No-shows",
                                "Avg service (min)", "Total serving (min)", "Avg wait of visitors (min)",
                                "Called within SLA (%)", "Share of served (%)", "Service time vs branch"),
                                rows),
                        new Section("Served per day", dayColumns, dayRows)))
                .notes(List.of(
                        "Served counts tickets the agent completed. Service time runs from start of serving to completion.",
                        "\"Service time vs branch\" is how much longer (+) or shorter (-) the agent's average is than the branch average."))
                .build();
    }

    private record AgentRow(String id, String name, List<Token> served, long noShows) {
    }

    // ==================== 2. WAITING TIME & SLA ====================

    public TabularReportDTO waitingAndSla(String branchId, LocalDate from, LocalDate to) {
        Range r = new Range(branchId, from, to);
        Map<String, Integer> sla = serviceSlaResolver.forBranch(branchId);
        LocalDateTime cap = r.cap();

        List<Wait> waits = r.tokens.stream().map(t -> {
            int slaSec = ServiceSlaResolver.slaSeconds(t, sla);
            Long wait = ServiceSlaResolver.waitSeconds(t);
            if (wait != null) return new Wait(t, wait, slaSec, true, wait > slaSec);
            // Not called (yet): a breach once it has waited past its SLA
            long waitedSoFar = Math.max(0, Duration.between(t.getCreatedAt(), cap).getSeconds());
            return new Wait(t, waitedSoFar, slaSec, false, waitedSoFar > slaSec);
        }).toList();

        List<Wait> called = waits.stream().filter(Wait::called).toList();
        List<Long> sorted = called.stream().map(Wait::seconds).sorted().toList();
        long breaches = waits.stream().filter(Wait::breached).count();
        long judged = waits.stream().filter(w -> w.called || w.breached).count();
        double rate = pct(judged - breaches, judged);

        List<Stat> summary = List.of(
                new Stat("Tokens", String.valueOf(waits.size())),
                new Stat("Average wait", minutes(avgSeconds(called) / 60)),
                new Stat("90% called within", sorted.isEmpty() ? "-" : minutes(percentile(sorted, 90) / 60.0)),
                new Stat("Longest wait", sorted.isEmpty() ? "-" : minutes(sorted.get(sorted.size() - 1) / 60.0)),
                new Stat("SLA breaches", String.valueOf(breaches), breaches > 0 ? "BAD" : "GOOD"),
                new Stat("Within SLA", String.format("%.1f%% (target %.0f%%)", rate, SLA_RATE_TARGET),
                        rate >= SLA_RATE_TARGET ? "GOOD" : "BAD"),
                new Stat("Not called yet", String.valueOf(waits.size() - called.size())));

        Map<String, String> serviceNames = new HashMap<>();
        r.tokens.forEach(t -> serviceNames.putIfAbsent(t.getServiceId(), t.getServiceName()));
        List<List<Object>> byService = group(waits, w -> w.token.getServiceId()).entrySet().stream()
                .map(e -> {
                    List<Wait> ws = e.getValue();
                    return List.<Object>of(
                            Objects.toString(serviceNames.get(e.getKey()), "-"),
                            round(ws.get(0).slaSeconds / 60.0),
                            ws.size(),
                            round(avgSeconds(onlyCalled(ws)) / 60),
                            round(maxSeconds(onlyCalled(ws)) / 60.0),
                            ws.stream().filter(Wait::breached).count(),
                            round(withinRate(ws)));
                })
                .sorted(Comparator.comparing(row -> -((Number) row.get(2)).longValue()))
                .toList();

        List<List<Object>> byDay = group(waits, w -> w.token.getCreatedAt().toLocalDate().toString())
                .entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(),
                        round(avgSeconds(onlyCalled(e.getValue())) / 60),
                        e.getValue().stream().filter(Wait::breached).count(),
                        round(withinRate(e.getValue()))))
                .toList();

        List<List<Object>> byHour = group(waits, w -> hour(w.token.getCreatedAt())).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(),
                        round(avgSeconds(onlyCalled(e.getValue())) / 60),
                        e.getValue().stream().filter(Wait::breached).count(),
                        round(withinRate(e.getValue()))))
                .toList();

        List<List<Object>> worst = waits.stream()
                .filter(Wait::breached)
                .sorted(Comparator.comparingLong((Wait w) -> w.seconds - w.slaSeconds).reversed())
                .limit(25)
                .map(w -> List.<Object>of(
                        w.token.getToken(),
                        w.token.getCreatedAt().toLocalDate().toString(),
                        Objects.toString(w.token.getServiceName(), "-"),
                        round(w.seconds / 60.0),
                        round(w.slaSeconds / 60.0),
                        round((w.seconds - w.slaSeconds) / 60.0),
                        Objects.toString(w.token.getAssignedCounterName(), "-"),
                        w.called ? w.token.getStatus().name() : "NOT CALLED"))
                .toList();

        return r.report("WAITING_SLA", "Waiting Time & SLA")
                .summary(summary)
                .sections(List.of(
                        new Section("By service", List.of("Service", "SLA (min)", "Tokens", "Avg wait (min)",
                                "Longest wait (min)", "Breaches", "Within SLA (%)"), byService),
                        new Section("By day", List.of("Date", "Tokens", "Avg wait (min)", "Breaches",
                                "Within SLA (%)"), byDay),
                        new Section("By hour of arrival", List.of("Hour", "Tokens", "Avg wait (min)", "Breaches",
                                "Within SLA (%)"), byHour),
                        new Section("Longest delays", List.of("Ticket", "Date", "Service", "Waited (min)",
                                "SLA (min)", "Over by (min)", "Counter", "Status"), worst)))
                .notes(List.of(
                        "Waiting time runs from taking the ticket to the first call.",
                        "Each ticket is judged against its own service's SLA (a sub-service without one uses its main service's, otherwise 15 min).",
                        "A ticket that was never called counts as a breach once it has waited longer than its SLA."))
                .build();
    }

    private record Wait(Token token, long seconds, int slaSeconds, boolean called, boolean breached) {
    }

    private static List<Wait> onlyCalled(List<Wait> ws) {
        return ws.stream().filter(Wait::called).toList();
    }

    private static double avgSeconds(List<Wait> ws) {
        return ws.stream().mapToLong(Wait::seconds).average().orElse(0);
    }

    private static long maxSeconds(List<Wait> ws) {
        return ws.stream().mapToLong(Wait::seconds).max().orElse(0);
    }

    private static double withinRate(List<Wait> ws) {
        long judged = ws.stream().filter(w -> w.called || w.breached).count();
        long breached = ws.stream().filter(Wait::breached).count();
        return pct(judged - breached, judged);
    }

    private static double percentile(List<Long> sorted, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    // ==================== 3. NO-SHOW ANALYSIS ====================

    public TabularReportDTO noShowAnalysis(String branchId, LocalDate from, LocalDate to) {
        Range r = new Range(branchId, from, to);
        List<Token> called = r.tokens.stream().filter(OperationsReportService::wasCalled).toList();
        List<Token> noShows = called.stream().filter(OperationsReportService::wasNoShow).toList();
        long recovered = noShows.stream().filter(OperationsReportService::isServed).count();
        List<Token> repeated = called.stream().filter(t -> calls(t) > 1).toList();
        long extraCalls = repeated.stream().mapToLong(t -> calls(t) - 1).sum();
        double rate = pct(noShows.size(), called.size());

        List<Stat> summary = List.of(
                new Stat("Tokens called", String.valueOf(called.size())),
                new Stat("No-shows", String.valueOf(noShows.size())),
                new Stat("No-show rate", String.format("%.1f%% (limit %.0f%%)", rate, NO_SHOW_RATE_LIMIT),
                        rate <= NO_SHOW_RATE_LIMIT ? "GOOD" : "BAD"),
                new Stat("Called back and served", String.valueOf(recovered)),
                new Stat("Called more than once", String.valueOf(repeated.size())),
                new Stat("Extra calls", String.valueOf(extraCalls)));

        // By agent: who called the visitor (the no-show agent if marked no-show)
        Function<Token, String> agentOf = t -> wasNoShow(t) && t.getNoShowUserId() != null
                ? t.getNoShowUserId() : t.getServedByUserId();
        Map<String, String> names = userNames(called.stream().map(agentOf).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<List<Object>> byAgent = noShowRows(called, agentOf, id -> names.getOrDefault(id, "Unknown"));

        Function<Token, String> counterOf = t -> wasNoShow(t) && t.getNoShowCounterId() != null
                ? t.getNoShowCounterId() : t.getAssignedCounterId();
        Map<String, String> counterNames = counterRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(Counter::getId, Counter::getName, (a, b) -> a));
        List<List<Object>> byCounter = noShowRows(called, counterOf, id -> counterNames.getOrDefault(id, "Unknown"));

        List<List<Object>> byDay = group(called, t -> t.getCreatedAt().toLocalDate().toString()).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(),
                        countIf(e.getValue(), OperationsReportService::wasNoShow),
                        round(pct(countIf(e.getValue(), OperationsReportService::wasNoShow), e.getValue().size())),
                        e.getValue().stream().mapToLong(t -> Math.max(0, calls(t) - 1)).sum()))
                .toList();

        List<List<Object>> byHour = group(called, t -> hour(t.getCreatedAt())).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(),
                        countIf(e.getValue(), OperationsReportService::wasNoShow),
                        round(pct(countIf(e.getValue(), OperationsReportService::wasNoShow), e.getValue().size()))))
                .toList();

        List<List<Object>> byService = noShowRows(called, Token::getServiceName, Function.identity());

        List<List<Object>> repeatRows = repeated.stream()
                .sorted(Comparator.comparingInt(OperationsReportService::calls).reversed())
                .limit(50)
                .map(t -> List.<Object>of(
                        t.getToken(),
                        t.getCreatedAt().toLocalDate().toString(),
                        Objects.toString(t.getServiceName(), "-"),
                        calls(t),
                        noShowCount(t),
                        t.getStatus().name(),
                        Objects.toString(t.getAssignedCounterName(), "-")))
                .toList();

        List<String> rateColumns = List.of("Called", "No-shows", "No-show rate (%)", "Share of all no-shows (%)");
        return r.report("NO_SHOW", "No-Show Analysis")
                .summary(summary)
                .sections(List.of(
                        new Section("By agent", prepend("Agent", rateColumns), byAgent),
                        new Section("By counter", prepend("Counter", rateColumns), byCounter),
                        new Section("By service", prepend("Service", rateColumns), byService),
                        new Section("By day", List.of("Date", "Called", "No-shows", "No-show rate (%)",
                                "Repeat calls"), byDay),
                        new Section("By hour of arrival", List.of("Hour", "Called", "No-shows",
                                "No-show rate (%)"), byHour),
                        new Section("Tickets called more than once", List.of("Ticket", "Date", "Service",
                                "Calls", "No-shows", "Final status", "Counter"), repeatRows)))
                .notes(List.of(
                        "A no-show is a ticket called to a counter where the visitor did not come. It still counts if the visitor was called back later.",
                        "Repeat calls are recalls and calls back from hold or no-show. Counting started on 2026-10-25, so earlier tickets show one call."))
                .build();
    }

    private List<List<Object>> noShowRows(List<Token> called, Function<Token, String> key,
                                          Function<String, String> label) {
        long allNoShows = countIf(called, OperationsReportService::wasNoShow);
        return group(called.stream().filter(t -> key.apply(t) != null).toList(), key).entrySet().stream()
                .map(e -> {
                    long ns = countIf(e.getValue(), OperationsReportService::wasNoShow);
                    return List.<Object>of(label.apply(e.getKey()), e.getValue().size(), ns,
                            round(pct(ns, e.getValue().size())), round(pct(ns, allNoShows)));
                })
                .sorted(Comparator.comparing(row -> -((Number) row.get(2)).longValue()))
                .toList();
    }

    // ==================== 4. SERVICE DEMAND & PEAK HOURS ====================

    public TabularReportDTO serviceDemand(String branchId, LocalDate from, LocalDate to) {
        Range r = new Range(branchId, from, to);
        List<Token> tokens = r.tokens;
        Map<String, Services> services = serviceRepository.findByBranchId(branchId).stream()
                .collect(Collectors.toMap(Services::getId, Function.identity()));
        Function<Token, String> categoryOf = t -> {
            Services s = services.get(t.getServiceId());
            if (s == null) return Objects.toString(t.getServiceName(), "-");
            Services parent = s.getParentId() != null ? services.get(s.getParentId()) : null;
            return parent != null ? parent.getName() : s.getName();
        };
        long days = ChronoUnit.DAYS.between(r.from, r.to) + 1;

        Map<String, List<Token>> hours = group(tokens, t -> hour(t.getCreatedAt()));
        List<List<Object>> byHour = hours.entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(), round((double) e.getValue().size() / days),
                        round(avgWaitMinutes(e.getValue())), round(pct(e.getValue().size(), tokens.size()))))
                .toList();

        Map<DayOfWeek, List<Token>> weekdays = tokens.stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().getDayOfWeek(), TreeMap::new, Collectors.toList()));
        List<List<Object>> byWeekday = weekdays.entrySet().stream()
                .map(e -> {
                    long occurrences = countWeekdays(r.from, r.to, e.getKey());
                    return List.<Object>of(e.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            e.getValue().size(), round((double) e.getValue().size() / Math.max(1, occurrences)),
                            round(pct(e.getValue().size(), tokens.size())));
                })
                .toList();

        List<List<Object>> byCategory = group(tokens, categoryOf).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(),
                        countIf(e.getValue(), OperationsReportService::isServed),
                        round(pct(e.getValue().size(), tokens.size())),
                        round(avgWaitMinutes(e.getValue())), round(avgServiceMinutes(e.getValue()))))
                .sorted(Comparator.comparing(row -> -((Number) row.get(1)).longValue()))
                .toList();

        List<List<Object>> byService = group(tokens, t -> Objects.toString(t.getServiceName(), "-")).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), categoryOf.apply(e.getValue().get(0)), e.getValue().size(),
                        countIf(e.getValue(), OperationsReportService::isServed),
                        round(pct(e.getValue().size(), tokens.size())),
                        round(avgWaitMinutes(e.getValue())), round(avgServiceMinutes(e.getValue()))))
                .sorted(Comparator.comparing(row -> -((Number) row.get(2)).longValue()))
                .toList();

        List<Token> served = tokens.stream().filter(OperationsReportService::isServed).toList();
        List<List<Object>> counterLoad = group(served.stream().filter(t -> t.getAssignedCounterName() != null).toList(),
                Token::getAssignedCounterName).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(), e.getValue().size(),
                        round(totalServiceMinutes(e.getValue())), round(avgServiceMinutes(e.getValue())),
                        round(pct(e.getValue().size(), served.size()))))
                .sorted(Comparator.comparing(row -> -((Number) row.get(1)).longValue()))
                .toList();

        List<List<Object>> byDay = group(tokens, t -> t.getCreatedAt().toLocalDate().toString()).entrySet().stream()
                .map(e -> List.<Object>of(e.getKey(),
                        e.getKey().isEmpty() ? "" : LocalDate.parse(e.getKey()).getDayOfWeek()
                                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        e.getValue().size(), countIf(e.getValue(), OperationsReportService::isServed)))
                .toList();

        List<Stat> summary = new ArrayList<>();
        summary.add(new Stat("Tokens", String.valueOf(tokens.size())));
        summary.add(new Stat("Average per day", String.format("%.1f", (double) tokens.size() / days)));
        top(hours).ifPresent(e -> summary.add(new Stat("Busiest hour", e.getKey() + " (" + e.getValue().size() + ")")));
        top(group(tokens, t -> t.getCreatedAt().toLocalDate().toString()))
                .ifPresent(e -> summary.add(new Stat("Busiest day", e.getKey() + " (" + e.getValue().size() + ")")));
        weekdays.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size()))
                .ifPresent(e -> summary.add(new Stat("Busiest weekday",
                        e.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH))));
        if (!byService.isEmpty()) {
            summary.add(new Stat("Top service", byService.get(0).get(0) + " (" + byService.get(0).get(2) + ")"));
        }

        return r.report("SERVICE_DEMAND", "Service Demand & Peak Hours")
                .summary(summary)
                .sections(List.of(
                        new Section("By hour of arrival", List.of("Hour", "Tokens", "Avg per day",
                                "Avg wait (min)", "Share (%)"), byHour),
                        new Section("By weekday", List.of("Day", "Tokens", "Avg per such day", "Share (%)"), byWeekday),
                        new Section("By service category", List.of("Category", "Tokens", "Served", "Share (%)",
                                "Avg wait (min)", "Avg service (min)"), byCategory),
                        new Section("By service", List.of("Service", "Category", "Tokens", "Served", "Share (%)",
                                "Avg wait (min)", "Avg service (min)"), byService),
                        new Section("Counter workload", List.of("Counter", "Served", "Serving time (min)",
                                "Avg service (min)", "Share of served (%)"), counterLoad),
                        new Section("By day", List.of("Date", "Day", "Tokens", "Served"), byDay)))
                .notes(List.of("A category is the main service; its sub-services are counted under it."))
                .build();
    }

    // ==================== 5. AGENT UTILIZATION ====================

    public TabularReportDTO agentUtilization(String branchId, LocalDate from, LocalDate to) {
        Range r = new Range(branchId, from, to);
        LocalDateTime cap = r.cap();
        List<UserSession> sessions = userSessionRepository.findOverlapping(branchId, UserRole.USER, r.start, r.end);
        List<CounterBreak> breaks = counterBreakRepository.findOverlapping(branchId, r.start, r.end);
        // Serving time comes from tokens served in the range
        List<Token> servedTimes = r.tokens.stream()
                .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                .toList();

        Set<String> agentIds = new LinkedHashSet<>();
        sessions.forEach(s -> addIfSet(agentIds, s.getUserId()));
        servedTimes.forEach(t -> addIfSet(agentIds, t.getServedByUserId()));
        Map<String, String> names = userNames(agentIds);

        double totLogged = 0, totServing = 0, totBreak = 0, totIdle = 0;
        long totBreaks = 0;
        List<List<Object>> agentRows = new ArrayList<>();
        for (String id : agentIds) {
            List<UserSession> mine = sessions.stream().filter(s -> id.equals(s.getUserId())).toList();
            List<CounterBreak> myBreaks = breaks.stream().filter(b -> id.equals(b.getUserId())).toList();
            double logged = mine.stream()
                    .mapToDouble(s -> overlapMinutes(s.getLoginAt(), s.getLogoutAt(), r.start, cap)).sum();
            double serving = servedTimes.stream().filter(t -> id.equals(t.getServedByUserId()))
                    .mapToDouble(t -> overlapMinutes(t.getStartAt(), t.getEndAt(), r.start, cap)).sum();
            double onBreak = myBreaks.stream()
                    .mapToDouble(b -> overlapMinutes(b.getStartedAt(), b.getEndedAt(), r.start, cap)).sum();
            double idle = Math.max(0, logged - serving - onBreak);
            double available = logged - onBreak;
            long days = mine.stream().map(s -> s.getLoginAt().toLocalDate()).distinct().count();

            totLogged += logged;
            totServing += serving;
            totBreak += onBreak;
            totIdle += idle;
            totBreaks += myBreaks.size();
            agentRows.add(List.of(names.getOrDefault(id, "Unknown"), days, round(logged), round(serving),
                    round(idle), myBreaks.size(), round(onBreak), round(available > 0 ? pct(serving, available) : 0)));
        }
        agentRows.sort(Comparator.comparing(row -> -((Number) row.get(7)).doubleValue()));

        List<Counter> counters = counterRepository.findByBranchIdOrderByCreatedAtAsc(branchId);
        List<List<Object>> counterRows = new ArrayList<>();
        for (Counter c : counters) {
            double staffed = sessions.stream().filter(s -> c.getId().equals(s.getCounterId()))
                    .mapToDouble(s -> overlapMinutes(s.getLoginAt(), s.getLogoutAt(), r.start, cap)).sum();
            List<CounterBreak> cb = breaks.stream().filter(b -> c.getId().equals(b.getCounterId())).toList();
            double onBreak = cb.stream()
                    .mapToDouble(b -> overlapMinutes(b.getStartedAt(), b.getEndedAt(), r.start, cap)).sum();
            double serving = servedTimes.stream().filter(t -> c.getId().equals(t.getAssignedCounterId()))
                    .mapToDouble(t -> overlapMinutes(t.getStartAt(), t.getEndAt(), r.start, cap)).sum();
            double available = Math.max(0, staffed - onBreak);
            counterRows.add(List.of(c.getName(), round(staffed), cb.size(), round(onBreak), round(available),
                    round(staffed > 0 ? pct(available, staffed) : 0),
                    round(available > 0 ? pct(serving, available) : 0),
                    counterNow(c)));
        }

        double availableTotal = totLogged - totBreak;
        List<Stat> summary = List.of(
                new Stat("Agents", String.valueOf(agentIds.size())),
                new Stat("Logged in", hours(totLogged)),
                new Stat("Serving", hours(totServing)),
                new Stat("Idle", hours(totIdle)),
                new Stat("Breaks", hours(totBreak) + " (" + totBreaks + ")"),
                new Stat("Utilization", String.format("%.1f%%", availableTotal > 0 ? pct(totServing, availableTotal) : 0)));

        return r.report("AGENT_UTILIZATION", "Agent Utilization")
                .summary(summary)
                .sections(List.of(
                        new Section("Agents", List.of("Agent", "Days", "Logged in (min)", "Serving (min)",
                                "Idle (min)", "Breaks", "Break time (min)", "Utilization (%)"), agentRows),
                        new Section("Counter availability", List.of("Counter", "Staffed (min)", "Breaks",
                                "Break time (min)", "Available (min)", "Available (%)", "Busy while available (%)",
                                "Now"), counterRows)))
                .notes(List.of(
                        "Logged-in time comes from agent check-in and check-out. Serving time runs from start of serving to completion.",
                        "Idle = logged in - serving - breaks. Utilization = serving / (logged in - breaks).",
                        "A break is the time a counter is paused. Break recording started on 2026-10-25."))
                .build();
    }

    private static String counterNow(Counter c) {
        if (!Boolean.TRUE.equals(c.getEnabled())) return "Disabled";
        if (Boolean.TRUE.equals(c.getPaused())) return "On break";
        if (c.getUserId() == null) return "Not staffed";
        return c.getStatus() != null ? c.getStatus().name() : "-";
    }

    // ==================== HELPERS ====================

    static boolean isServed(Token t) {
        return t.getStatus() == TokenStatus.DONE || t.getStatus() == TokenStatus.REVIEW;
    }

    static boolean wasCalled(Token t) {
        return calls(t) > 0 || t.getFirstCalledAt() != null || t.getStartAt() != null;
    }

    static boolean wasNoShow(Token t) {
        return noShowCount(t) > 0;
    }

    static int calls(Token t) {
        int n = t.getCallCount() != null ? t.getCallCount() : 0;
        return n == 0 && (t.getStartAt() != null || t.getAssignedCounterId() != null) ? 1 : n;
    }

    static int noShowCount(Token t) {
        int n = t.getNoShowCount() != null ? t.getNoShowCount() : 0;
        return n == 0 && t.getStatus() == TokenStatus.NO_SHOW ? 1 : n;
    }

    private Map<String, String> userNames(Collection<String> ids) {
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.toString(u.getName(), u.getEmail()), (a, b) -> a));
    }

    private static void addIfSet(Set<String> set, String id) {
        if (id != null && !id.isBlank()) set.add(id);
    }

    private static <T> Map<String, List<T>> group(List<T> items, Function<T, String> key) {
        return items.stream().collect(Collectors.groupingBy(
                t -> Objects.toString(key.apply(t), "-"), TreeMap::new, Collectors.toList()));
    }

    private static <T> Optional<Map.Entry<String, List<T>>> top(Map<String, List<T>> groups) {
        return groups.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size()));
    }

    private static <T> long countIf(List<T> items, Predicate<T> p) {
        return items.stream().filter(p).count();
    }

    private static List<String> prepend(String first, List<String> rest) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(rest);
        return all;
    }

    private static String hour(LocalDateTime t) {
        return String.format("%02d:00", t.getHour());
    }

    private static long countWeekdays(LocalDate from, LocalDate to, DayOfWeek day) {
        long n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) if (d.getDayOfWeek() == day) n++;
        return n;
    }

    private static double avgServiceMinutes(List<Token> tokens) {
        return tokens.stream()
                .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                .mapToDouble(t -> Duration.between(t.getStartAt(), t.getEndAt()).getSeconds() / 60.0)
                .average().orElse(0);
    }

    private static double totalServiceMinutes(List<Token> tokens) {
        return tokens.stream()
                .filter(t -> t.getStartAt() != null && t.getEndAt() != null)
                .mapToDouble(t -> Duration.between(t.getStartAt(), t.getEndAt()).getSeconds() / 60.0)
                .sum();
    }

    private static double avgWaitMinutes(List<Token> tokens) {
        return tokens.stream()
                .map(ServiceSlaResolver::waitSeconds)
                .filter(Objects::nonNull)
                .mapToDouble(s -> s / 60.0)
                .average().orElse(0);
    }

    private static double slaRate(List<Token> tokens, Map<String, Integer> sla) {
        List<Token> called = tokens.stream().filter(t -> ServiceSlaResolver.waitSeconds(t) != null).toList();
        return pct(countIf(called, t -> ServiceSlaResolver.withinSla(t, sla)), called.size());
    }

    /** Minutes of [start, end) inside [from, to); a null end means still running. */
    static double overlapMinutes(LocalDateTime start, LocalDateTime end, LocalDateTime from, LocalDateTime to) {
        if (start == null) return 0;
        LocalDateTime s = start.isAfter(from) ? start : from;
        LocalDateTime e = end == null || end.isAfter(to) ? to : end;
        return e.isAfter(s) ? Duration.between(s, e).getSeconds() / 60.0 : 0;
    }

    private static double pct(double part, double whole) {
        return whole > 0 ? part * 100.0 / whole : 0;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String minutes(double m) {
        return String.format("%.1f min", m);
    }

    private static String hours(double minutes) {
        long total = Math.round(minutes);
        return String.format("%dh %02dm", total / 60, total % 60);
    }
}
