package com.gis.servelq.services;

import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
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

    /** True when the ticket was called within its service's SLA. False when not called yet. */
    public static boolean withinSla(Token token, Map<String, Integer> slaByService) {
        Long wait = waitSeconds(token);
        return wait != null && wait <= slaSeconds(token, slaByService);
    }

    // ---- serving time ----

    public static int servingSlaSeconds(Token token, Map<String, Integer> servingSlaByService) {
        return servingSlaByService.getOrDefault(token.getServiceId(), DEFAULT_SERVING_SLA_SECONDS);
    }

    /** Seconds from start of serving to completion, or null when not finished. */
    public static Long servingSeconds(Token token) {
        if (token.getStartAt() == null || token.getEndAt() == null) return null;
        return Math.max(0, Duration.between(token.getStartAt(), token.getEndAt()).getSeconds());
    }

    /** True when serving finished within its service's serving SLA. False when not finished. */
    public static boolean withinServingSla(Token token, Map<String, Integer> servingSlaByService) {
        Long serving = servingSeconds(token);
        return serving != null && serving <= servingSlaSeconds(token, servingSlaByService);
    }

    /** % of finished tickets served within their serving SLA (0 when none finished). */
    public static double servingSlaRate(List<Token> tokens, Map<String, Integer> servingSlaByService) {
        long finished = tokens.stream().filter(t -> servingSeconds(t) != null).count();
        if (finished == 0) return 0;
        long within = tokens.stream().filter(t -> withinServingSla(t, servingSlaByService)).count();
        return within * 100.0 / finished;
    }
}
