package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponseDTO;
import com.gis.servelq.dto.TokenResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TVDisplayService tvDisplayService;
    private final AgentService agentService;

    public void broadcast(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void tvSocket(String branchId) {
        TVDisplayResponseDTO data = tvDisplayService.getTVDisplayData(branchId);
        broadcast("/topic/tv/" + branchId, data);
    }

    public void agentUpcoming(List<String> counterIds) {

        for (String counterId : counterIds) {
            List<TokenResponseDTO> data = agentService.getUpcomingTokensForCounter(counterId);
            broadcast("/topic/agent-upcoming/" + counterId, data);
        }
    }
}
