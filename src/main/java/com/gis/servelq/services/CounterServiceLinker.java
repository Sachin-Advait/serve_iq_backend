package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Keeps the counter <-> service many-to-many link consistent on both sides.
 *
 * The link is stored twice: Services.counterIds (which counters serve a
 * service, copied onto each token to route it) and Counter.serviceIds (which
 * services a counter handles). Assigning a service from the counter screen
 * used to only set Counter.serviceId, so the service kept an empty counter
 * list and its tokens showed up in every counter's upcoming queue. Every
 * change now goes through here so both sides always agree.
 */
@Component
@RequiredArgsConstructor
public class CounterServiceLinker {

    private final CounterRepository counterRepository;
    private final ServiceRepository serviceRepository;
    private final TokenRepository tokenRepository;
    private final TokenEventPublisher tokenEventPublisher;

    /**
     * Sets the services a counter handles and updates each affected service's
     * counter list. The counter itself is not saved.
     */
    public void setCounterServices(Counter counter, List<String> serviceIds) {
        List<String> next = clean(serviceIds);
        List<Services> services = serviceRepository.findAllById(next);
        if (services.size() != next.size()) {
            throw new ResourceNotFoundException("One or more services were not found");
        }
        for (Services s : services) {
            if (!Objects.equals(s.getBranchId(), counter.getBranchId())) {
                throw new BusinessException("Service " + s.getName() + " belongs to another branch");
            }
        }

        List<String> previous = serviceIdsOf(counter);
        Set<String> touched = new LinkedHashSet<>(previous);
        touched.addAll(next);

        for (Services s : serviceRepository.findAllById(touched)) {
            List<String> counterIds = new ArrayList<>(clean(s.getCounterIds()));
            boolean changed;
            if (next.contains(s.getId())) {
                changed = !counterIds.contains(counter.getId()) && counterIds.add(counter.getId());
            } else {
                changed = counterIds.remove(counter.getId());
            }
            if (changed) {
                s.setCounterIds(counterIds);
                serviceRepository.save(s);
            }
        }

        applyServiceIds(counter, next);
        rerouteWaitingTokens(counter.getBranchId());
    }

    /**
     * Sets the counters that serve a service and updates each affected
     * counter's service list. The service must already be saved (it needs an
     * id); the service itself is not saved again here.
     */
    public void setServiceCounters(Services service, List<String> counterIds) {
        List<String> next = clean(counterIds);
        List<Counter> counters = counterRepository.findAllById(next);
        if (counters.size() != next.size()) {
            throw new ResourceNotFoundException("One or more counters were not found");
        }
        for (Counter c : counters) {
            if (!Objects.equals(c.getBranchId(), service.getBranchId())) {
                throw new BusinessException("Counter " + c.getName() + " belongs to another branch");
            }
        }

        List<String> previous = clean(service.getCounterIds());
        Set<String> touched = new LinkedHashSet<>(previous);
        touched.addAll(next);

        for (Counter c : counterRepository.findAllById(touched)) {
            List<String> serviceIds = serviceIdsOf(c);
            boolean changed;
            if (next.contains(c.getId())) {
                changed = !serviceIds.contains(service.getId()) && serviceIds.add(service.getId());
            } else {
                changed = serviceIds.remove(service.getId());
            }
            if (changed) {
                applyServiceIds(c, serviceIds);
                counterRepository.save(c);
            }
        }

        service.setCounterIds(next.isEmpty() ? null : next);
        rerouteWaitingTokens(service.getBranchId());
    }

    /** Removes a counter that is being deleted from every service's counter list. */
    public void unlinkCounter(Counter counter) {
        for (Services s : serviceRepository.findAllById(serviceIdsOf(counter))) {
            List<String> counterIds = new ArrayList<>(clean(s.getCounterIds()));
            if (counterIds.remove(counter.getId())) {
                s.setCounterIds(counterIds.isEmpty() ? null : counterIds);
                serviceRepository.save(s);
            }
        }
        rerouteWaitingTokens(counter.getBranchId());
    }

    /** Removes a service that is being deleted from every counter's service list. */
    public void unlinkService(Services service) {
        for (Counter c : counterRepository.findAllById(clean(service.getCounterIds()))) {
            List<String> serviceIds = serviceIdsOf(c);
            if (serviceIds.remove(service.getId())) {
                applyServiceIds(c, serviceIds);
                counterRepository.save(c);
            }
        }
    }

    /**
     * Re-points today's waiting tokens in the branch at the counters their
     * service is assigned to now, so a change shows up in the agents' upcoming
     * queues straight away instead of only for tokens issued afterwards.
     * Transferred tokens keep the counter they were sent to.
     */
    private void rerouteWaitingTokens(String branchId) {
        Set<String> affectedCounters = new LinkedHashSet<>();
        boolean anyOpenToAll = false;
        for (Token token : tokenRepository.findByBranchIdAndStatusAndTokenDateOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.WAITING, LocalDate.now(), Pageable.unpaged())) {
            if (Boolean.TRUE.equals(token.getIsTransfer()) || !StringUtils.hasText(token.getServiceId())) {
                continue;
            }
            Services service = serviceRepository.findById(token.getServiceId()).orElse(null);
            if (service == null) {
                continue;
            }
            List<String> before = clean(token.getCounterIds());
            List<String> after = countersForService(service);
            if (new HashSet<>(before).equals(new HashSet<>(after))) {
                continue;
            }
            anyOpenToAll |= before.isEmpty() || after.isEmpty();
            affectedCounters.addAll(before);
            affectedCounters.addAll(after);
            token.setCounterIds(after.isEmpty() ? null : after);
            tokenRepository.save(token);
        }

        if (anyOpenToAll) {
            counterRepository.findByBranchId(branchId).forEach(c -> affectedCounters.add(c.getId()));
        }
        for (String counterId : affectedCounters) {
            tokenEventPublisher.publish(new TokenEvent(
                    TokenEventType.AGENT_QUEUE_CHANGED, branchId, null, null, counterId, Instant.now()));
        }
    }

    /**
     * Counters a new token for this service may be called from. A sub-service
     * with no counters of its own falls back to its parent's counters. An
     * empty result means the service is not assigned anywhere, and its tokens
     * are open to every counter.
     */
    public List<String> countersForService(Services service) {
        Set<String> seen = new HashSet<>();
        Services current = service;
        while (current != null && seen.add(current.getId())) {
            List<String> ids = clean(current.getCounterIds());
            if (!ids.isEmpty()) {
                return ids;
            }
            current = StringUtils.hasText(current.getParentId())
                    ? serviceRepository.findById(current.getParentId()).orElse(null)
                    : null;
        }
        return List.of();
    }

    /** The services a counter handles, including a legacy single serviceId. */
    public static List<String> serviceIdsOf(Counter counter) {
        List<String> ids = new ArrayList<>(clean(counter.getServiceIds()));
        if (StringUtils.hasText(counter.getServiceId()) && !ids.contains(counter.getServiceId())) {
            ids.add(0, counter.getServiceId());
        }
        return ids;
    }

    private static void applyServiceIds(Counter counter, List<String> serviceIds) {
        counter.setServiceIds(serviceIds.isEmpty() ? null : serviceIds);
        // serviceId stays as the counter's first service for older clients.
        counter.setServiceId(serviceIds.isEmpty() ? null : serviceIds.get(0));
    }

    private static List<String> clean(Collection<String> ids) {
        if (ids == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            if (StringUtils.hasText(id)) out.add(id.trim());
        }
        return new ArrayList<>(out);
    }
}
