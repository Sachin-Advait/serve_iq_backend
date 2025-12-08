package com.gis.servelq.controllers;

import com.gis.servelq.dto.AgentCallResponseDTO;
import com.gis.servelq.dto.RecentServiceDTO;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.dto.TokenTransferRequest;
import com.gis.servelq.models.Token;
import com.gis.servelq.services.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;

    @PostMapping("/counter/call-next/{counterId}")
    public ResponseEntity<AgentCallResponseDTO> callNextToken(@PathVariable String counterId) {
        AgentCallResponseDTO response = agentService.callNextToken(counterId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/start-serving/{tokenId}")
    public ResponseEntity<Void> startServingToken(@PathVariable String tokenId) {
        agentService.startServingToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/complete/{tokenId}")
    public ResponseEntity<Void> completeToken(@PathVariable String tokenId) {
        agentService.completeToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/counter/queue/{counterId}")
    public ResponseEntity<?> getCounterQueue(@PathVariable String counterId) {
        var tokens = agentService.getUpcomingTokensForCounter(counterId);
        return ResponseEntity.ok(tokens);

    }

    @GetMapping("/recent-services")
    public ResponseEntity<List<RecentServiceDTO>> getRecentServices() {
        List<RecentServiceDTO> recentServices = agentService.getRecentServices();
        return ResponseEntity.ok(recentServices);
    }

    @PostMapping("/recall")
    public ResponseEntity<AgentCallResponseDTO> recallToken(
            @RequestParam String tokenId
    ) {
        AgentCallResponseDTO response = agentService.recallToken(tokenId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/counter/active-token/{counterId}")
    public ResponseEntity<AgentCallResponseDTO> getActiveToken(@PathVariable String counterId) {
        AgentCallResponseDTO dto = agentService.getServingOrCallingTokenByCounter(counterId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TokenResponseDTO> transferToken(@RequestBody TokenTransferRequest request) {
        Token token = agentService.transferToken(request);
        return ResponseEntity.ok(TokenResponseDTO.fromEntity(token));
    }
}