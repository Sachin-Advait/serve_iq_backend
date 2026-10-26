package com.gis.servelq.services;

import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out each service's SLA: the longest a visitor should wait between
 * taking a ticket and being called. A sub-service without its own SLA uses its
 * parent's, and a service with neither uses {@link #DEFAULT_SLA_SECONDS}.
 */
@Component
@RequiredArgsConstructor
public class ServiceSlaResolver {

    public static final int DEFAULT_SLA_SECONDS = 15 * 60;

    /** Serving-time SLA when neither the service nor its parent has one. */
    public static final int DEFAULT_SERVING_SLA_SECONDS = 10 * 60;

    private final ServiceRepository serviceRepository;

    /** Service id to effective waiting-time SLA in seconds, for every service in the branch. */
    public Map<String, Integer> forBranch(String branchId) {
        return resolve(branchId, Services::getSlaSeconds, DEFAULT_SLA_SECONDS);
    }

    /** Service id to effective serving-time SLA in seconds, for every service in the branch. */
    public Map<String, Integer> servingForBranch(String branchId) {
        return resolve(branchId, Services::getServingSlaSeconds, DEFAULT_SERVING_SLA_SECONDS);
    }

    private Map<String, Integer> resolve(String branchId, Function<Services, Integer> field, int fallback) {
        List<Services> services = serviceRepository.findByBranchId(branchId);
        Map<String, Services> byId = services.stream()
                .collect(Collectors.toMap(Services::getId, Function.identity()));
        return services.stream()
                .collect(Collectors.toMap(Services::getId, s -> effective(s, byId, field, fallback)));
    }

    private static int effective(Services service, Map<String, Services> byId,
                                 Function<Services, Integer> field, int fallback) {
        if (isSet(field.apply(service))) return field.apply(service);
        Services parent = service.getParentId() != null ? byId.get(service.getParentId()) : null;
        if (parent != null && isSet(field.apply(parent))) return field.apply(parent);
        return fallback;
    }

    private static boolean isSet(Integer seconds) {
        return seconds != null && seconds > 0;
    }

    public static int slaSeconds(Token token, Map<String, Integer> slaByService) {
        return slaByService.getOrDefault(token.getServiceId(), DEFAULT_SLA_SECONDS);
    }

    public static int servingSlaSeconds(Token token, Map<String, Integer> servingSlaByService) {
        return servingSlaByService.getOrDefault(token.getServiceId(), DEFAULT_SERVING_SLA_SECONDS);
    }

    /**
     * How one ticket stands against an SLA. Only ON_TIME and BREACHED are
     * judged; PENDING (still inside its limit) and EXCLUDED (the SLA does not
     * apply) are left out of the SLA %.
     */
    public enum Verdict { ON_TIME, BREACHED, PENDING, EXCLUDED }

    /**
     * When the visitor was first called. Tokens from before first_called_at
     * existed only have startAt (serving started), which is the closest we have.
     */
    public static LocalDateTime calledAt(Token token) {
        return token.getFirstCalledAt() != null ? token.getFirstCalledAt() : token.getStartAt();
    }

    /** Seconds from ticket to first call, or null when not called yet. */
    public static Long waitSeconds(Token token) {
        LocalDateTime called = calledAt(token);
        if (token.getCreatedAt() == null || called == null) return null;
        return Math.max(0, Duration.between(token.getCreatedAt(), called).getSeconds());
    }

    /**
     * Waiting SLA. A called ticket is judged on ticket-to-first-call. A ticket
     * still waiting is BREACHED once it has waited past its limit by asOf, else
     * PENDING. Anything else that was never called (no-show or hold records
     * without call times) is EXCLUDED.
     */
    public static Verdict waitingVerdict(Token token, Map<String, Integer> slaByService, LocalDateTime asOf) {
        if (token.getCreatedAt() == null) return Verdict.EXCLUDED;
        int limit = slaSeconds(token, slaByService);
        Long wait = waitSeconds(token);
        if (wait != null) return wait <= limit ? Verdict.ON_TIME : Verdict.BREACHED;
        if (token.getStatus() != TokenStatus.WAITING) return Verdict.EXCLUDED;
        return elapsed(token.getCreatedAt(), asOf) > limit ? Verdict.BREACHED : Verdict.PENDING;
    }

    /** Seconds from start of serving to completion at the last counter, or null when not finished. */
    public static Long servingSeconds(Token token) {
        if (token.getStartAt() == null || token.getEndAt() == null) return null;
        return Math.max(0, Duration.between(token.getStartAt(), token.getEndAt()).getSeconds());
    }

    /** Serving time across all counters (earlier counters + the last one), or null when not finished. */
    public static Long totalServingSeconds(Token token) {
        Long last = servingSeconds(token);
        return last == null ? null : last + prior(token);
    }

    /**
     * Serving SLA at the last counter. A finished ticket is judged on
     * start-of-serving to completion; one still being served is BREACHED once
     * it runs past its limit by asOf, else PENDING. A ticket that was never
     * served (waiting, no-show) is EXCLUDED.
     */
    public static Verdict servingVerdict(Token token, Map<String, Integer> servingSlaByService, LocalDateTime asOf) {
        if (token.getStartAt() == null) return Verdict.EXCLUDED;
        return judge(servingSoFar(token, asOf), token.getEndAt() != null, servingSlaSeconds(token, servingSlaByService));
    }

    /**
     * Serving SLA over the visitor's whole visit: serving time at every counter
     * added up, against the ticket's service limit. Between counters after a
     * transfer it is judged on the earlier counters' time so far.
     */
    public static Verdict totalServingVerdict(Token token, Map<String, Integer> servingSlaByService, LocalDateTime asOf) {
        int limit = servingSlaSeconds(token, servingSlaByService);
        if (token.getStartAt() == null) {
            if (prior(token) == 0) return Verdict.EXCLUDED;
            return prior(token) > limit ? Verdict.BREACHED : Verdict.PENDING;
        }
        return judge(prior(token) + servingSoFar(token, asOf), token.getEndAt() != null, limit);
    }

    private static Verdict judge(long seconds, boolean finished, int limit) {
        if (finished) return seconds <= limit ? Verdict.ON_TIME : Verdict.BREACHED;
        return seconds > limit ? Verdict.BREACHED : Verdict.PENDING;
    }

    private static long servingSoFar(Token token, LocalDateTime asOf) {
        LocalDateTime end = token.getEndAt() != null ? token.getEndAt() : asOf;
        return elapsed(token.getStartAt(), end);
    }

    private static long prior(Token token) {
        return token.getPriorServingSeconds() == null ? 0 : token.getPriorServingSeconds();
    }

    private static long elapsed(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return 0;
        return Math.max(0, Duration.between(from, to).getSeconds());
    }

    /** Counts of each verdict for a list of tickets. */
    public record Tally(long onTime, long breached, long pending, long excluded) {
        public long judged() {
            return onTime + breached;
        }

        /** SLA % = on-time judged / all judged x 100 (0 when nothing can be judged yet). */
        public double rate() {
            return judged() == 0 ? 0 : onTime * 100.0 / judged();
        }
    }

    public static Tally tally(List<Token> tokens, Function<Token, Verdict> verdict) {
        long[] c = new long[4];
        tokens.forEach(t -> c[verdict.apply(t).ordinal()]++);
        return new Tally(c[0], c[1], c[2], c[3]);
    }

    public static Tally waitingTally(List<Token> tokens, Map<String, Integer> sla, LocalDateTime asOf) {
        return tally(tokens, t -> waitingVerdict(t, sla, asOf));
    }

    public static Tally servingTally(List<Token> tokens, Map<String, Integer> servingSla, LocalDateTime asOf) {
        return tally(tokens, t -> servingVerdict(t, servingSla, asOf));
    }

    public static Tally totalServingTally(List<Token> tokens, Map<String, Integer> servingSla, LocalDateTime asOf) {
        return tally(tokens, t -> totalServingVerdict(t, servingSla, asOf));
    }

    /** The moment tickets are judged at: now, or the end of a period that is already over. */
    public static LocalDateTime asOf(LocalDateTime periodEnd) {
        LocalDateTime now = LocalDateTime.now();
        return periodEnd != null && periodEnd.isBefore(now) ? periodEnd : now;
    }
}
