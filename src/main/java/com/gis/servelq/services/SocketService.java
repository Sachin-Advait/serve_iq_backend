package com.gis.servelq.services;

import com.gis.servelq.dto.AppDashboardDTO;
import com.gis.servelq.dto.CounterDisplayDTO;
import com.gis.servelq.dto.TVDisplayResponseDTO;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.models.AppType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TVDisplayService tvDisplayService;
    private final AppConfigService appConfigService;
    private final CounterService counterService;

    public void broadcast(String destination, Object payload) {
        log.info("🔥 WEBSOCKET PUSH → destination={}", destination);
        log.debug("WEBSOCKET payload={}", payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void notifyCounterDisplayImage(String branchId, Object payload) {
        broadcast("/topic/counter-display/image/" + branchId, payload);
    }

    public void notifyCounterDisplay(String counterId, Object payload) {
        broadcast("/topic/counter-display/" + counterId, payload);
    }

    public void notifyCounter(String counterId, Object payload) {
        broadcast("/topic/counter/" + counterId, payload);
    }

    public void notifyBranchTV(String branchId) {
        TVDisplayResponseDTO data = tvDisplayService.getTVDisplayData(branchId);
        broadcast("/topic/tv/" + branchId, data);
    }

    public void notifyMeetingTV(String branchId) {
        List<CounterDisplayDTO> data = counterService.getCounterDisplayBoard(branchId);
        broadcast("/topic/meeting-tv/" + branchId, data);
    }

    public void notifyBranchTVMedia(String branchId, Object payload) {
        broadcast("/topic/tv-media/" + branchId, payload);
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
