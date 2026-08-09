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
@RequestMapping("/serveiq/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;

    @PostMapping("/call-next-token/{counterId}")
    public ResponseEntity<AgentCallResponseDTO> callNextToken(@PathVariable String counterId) {
        AgentCallResponseDTO response = agentService.callNextToken(counterId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/call-hold-token/{tokenId}")
    public ResponseEntity<AgentCallResponseDTO> callHoldToken(@PathVariable String tokenId) {
        AgentCallResponseDTO response = agentService.callHoldToken(tokenId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/start-serving/{tokenId}")
    public ResponseEntity<Void> startServingToken(@PathVariable String tokenId) throws Exception {
        agentService.startServingToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/complete/{tokenId}")
    public ResponseEntity<Void> completeToken(@PathVariable String tokenId) throws Exception {
        agentService.completeToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/queue/{counterId}")
    public ResponseEntity<?> getCounterQueue(@PathVariable String counterId) {
        var tokens = agentService.getUpcomingTokensForCounter(counterId);
        return ResponseEntity.ok(tokens);

    }

    @GetMapping("/recent-services/{counterId}")
    public ResponseEntity<List<RecentServiceDTO>> getRecentServices(@PathVariable String counterId) {
        List<RecentServiceDTO> recentServices = agentService.getRecentServices(counterId);
        return ResponseEntity.ok(recentServices);
    }

    @PostMapping("/token/recall/{tokenId}")
    public ResponseEntity<AgentCallResponseDTO> recallToken(@PathVariable String tokenId) throws Exception {
        AgentCallResponseDTO response = agentService.recallToken(tokenId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active-token/{counterId}")
    public ResponseEntity<AgentCallResponseDTO> getActiveToken(@PathVariable String counterId) {
        AgentCallResponseDTO dto = agentService.getServingOrCallingTokenByCounter(counterId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/token/transfer")
    public ResponseEntity<TokenResponseDTO> transferToken(@RequestBody TokenTransferRequest request) {
        Token token = agentService.transferToken(request);
        return ResponseEntity.ok(TokenResponseDTO.fromEntity(token));
    }

    @PostMapping("/hold-token/{tokenId}")
    public ResponseEntity<TokenResponseDTO> holdToken(@PathVariable String tokenId) {
        Token token = agentService.holdToken(tokenId);
        return ResponseEntity.ok(TokenResponseDTO.fromEntity(token));
    }

    @PostMapping("/no-show-token/{tokenId}")
    public ResponseEntity<TokenResponseDTO> noShow(@PathVariable String tokenId) {
        Token token = agentService.noShow(tokenId);
        return ResponseEntity.ok(TokenResponseDTO.fromEntity(token));
    }
}