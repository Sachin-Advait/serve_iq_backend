package com.gis.servelq.controllers;

import com.gis.servelq.dto.AgentCallResponse;
import com.gis.servelq.services.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final TokenService tokenService;

    @PostMapping("/counter/call-next/{counterId}")
    public ResponseEntity<AgentCallResponse> callNextToken(@PathVariable String counterId) {
        AgentCallResponse response = tokenService.callNextToken(counterId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/start-serving/{tokenId}")
    public ResponseEntity<Void> startServingToken(@PathVariable String tokenId) {
        tokenService.startServingToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/complete/{tokenId}")
    public ResponseEntity<Void> completeToken(@PathVariable String tokenId) {
        tokenService.completeToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/counter/queue/{counterId}")
    public ResponseEntity<?> getCounterQueue(@PathVariable String counterId) {
        var tokens = tokenService.getUpcomingTokensForCounter(counterId);
        return ResponseEntity.ok(tokens);

    }
}