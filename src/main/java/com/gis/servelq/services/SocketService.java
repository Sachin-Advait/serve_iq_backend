package com.gis.servelq.services;

import com.gis.servelq.dto.AppDashboardDTO;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.models.AppType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TVDisplayService tvDisplayService;
    private final AppConfigService appConfigService;
    private final CounterService counterService;
    private final SocketVersionService socketVersionService;

    public void broadcast(String destination, Object payload) {
        log.info("🔥 WEBSOCKET PUSH → destination={}", destination);
        log.debug("WEBSOCKET payload={}", payload);
        long version = socketVersionService.next(destination);
        messagingTemplate.convertAndSend(destination, payload, Map.of("version", String.valueOf(version)));
    }

    private final Map<String, Object> pushLocks = new ConcurrentHashMap<>();

    /**
     * Loads the data and sends it while holding a lock for the destination.
     * Two requests finishing at once (Counter 4 calls C008 while Counter 2
     * completes its token) each load the list after their own commit; without
     * the lock the one that loaded first, still showing C008, could be sent
     * last and win, and its newer version number hid the loss from the
     * heartbeat check. Inside the lock the last list sent is the last loaded.
     */
    private void pushLatest(String destination, Supplier<?> load) {
        synchronized (pushLocks.computeIfAbsent(destination, k -> new Object())) {
            broadcast(destination, load.get());
        }
    }

    public void pushCounterDisplay(String counterId, Supplier<?> load) {
        pushLatest("/topic/counter-display/" + counterId, load);
    }

    public void pushAgentQueue(String counterId, Supplier<List<TokenResponseDTO>> load) {
        pushLatest("/topic/agent-upcoming/" + counterId, load);
    }

    public void notifyCounterDisplay(String counterId, Object payload) {
        broadcast("/topic/counter-display/" + counterId, payload);
    }

    public void notifyCounter(String counterId, Object payload) {
        broadcast("/topic/counter/" + counterId, payload);
    }

    public void notifyBranchTV(String branchId) {
        pushLatest("/topic/tv/" + branchId, () -> tvDisplayService.getTVDisplayData(branchId));
    }

    public void notifyMeetingTV(String branchId) {
        pushLatest("/topic/meeting-tv/" + branchId, () -> counterService.getCounterDisplayBoard(branchId));
    }

    public void notifyAgentQueue(String counterId, List<TokenResponseDTO> payload) {
        broadcast("/topic/agent-upcoming/" + counterId, payload);
    }

    public void broadcastAppDashboard(AppType appType) {
        try {
            AppDashboardDTO dashboard = appConfigService.getAppDashboard(appType);
            String destination = "/topic/app-config/" + appType.name().toLowerCase();
            broadcast(destination, dashboard);
            log.info("Broadcast {} dashboard to {}", appType, destination);
        } catch (Exception e) {
            log.error("Failed to broadcast dashboard for {}", appType, e);
        }
    }
}
