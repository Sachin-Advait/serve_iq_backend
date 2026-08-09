package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponseDTO;
import com.gis.servelq.dto.TokenResponseDTO;
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

    public void notifyBranchTVMedia(String branchId, Object payload) {
        broadcast("/topic/tv-media/" + branchId, payload);
    }

    public void notifyAgentQueue(String counterId, List<TokenResponseDTO> payload) {
        broadcast("/topic/agent-upcoming/" + counterId, payload);
    }
}
