package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TVDisplayService tvDisplayService;

    public void broadcast(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    public void tvSocket(String branchId) {
        TVDisplayResponseDTO data = tvDisplayService.getTVDisplayData(branchId);
        broadcast("/topic/tv/" + branchId, data);
    }
}
