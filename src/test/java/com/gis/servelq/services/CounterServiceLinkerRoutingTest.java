package com.gis.servelq.services;

import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.models.Services;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** A counter on a main service serves all its sub-services; a counter can also take single sub-services. */
@ExtendWith(MockitoExtension.class)
class CounterServiceLinkerRoutingTest {

    @Mock private CounterRepository counterRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private TokenEventPublisher tokenEventPublisher;
    @InjectMocks private CounterServiceLinker linker;

    private static Services service(String id, String parentId, List<String> counterIds) {
        Services s = new Services();
        s.setId(id);
        s.setParentId(parentId);
        s.setCounterIds(counterIds);
        return s;
    }

    @Test
    void subServiceGetsItsOwnAndItsMainServiceCounters() {
        Services main = service("m", null, List.of("y"));
        Services picked = service("a1", "m", List.of("x"));
        Services other = service("a2", "m", null);
        when(serviceRepository.findById("m")).thenReturn(Optional.of(main));

        assertThat(linker.countersForService(picked)).containsExactly("x", "y");
        assertThat(linker.countersForService(other)).containsExactly("y");
        assertThat(linker.countersForService(main)).containsExactly("y");
    }

    @Test
    void unassignedServiceIsOpenToAll() {
        Services main = service("m", null, null);
        Services sub = service("a1", "m", null);
        when(serviceRepository.findById("m")).thenReturn(Optional.of(main));

        assertThat(linker.countersForService(sub)).isEmpty();
    }
}
