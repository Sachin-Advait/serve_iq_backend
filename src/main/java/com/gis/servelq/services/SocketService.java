package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponse;
import com.gis.servelq.dto.TokenDTO;
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
        TVDisplayResponse data = tvDisplayService.getTVDisplayData(branchId);
        broadcast("/topic/tv/" + branchId, data);
    }

    public void agentUpcoming(String counterId) {
        List<TokenDTO> data = agentService.getUpcomingTokensForCounter(counterId);
        broadcast("/topic/agent-upcoming/" + counterId, data);
    }
}
