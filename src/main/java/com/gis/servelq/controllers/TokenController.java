package com.gis.servelq.controllers;

import com.gis.servelq.dto.LiveTokenDTO;
import com.gis.servelq.dto.TokenDTO;
import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponse;
import com.gis.servelq.models.Token;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.services.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {
    private final TokenService tokenService;
    private final TokenRepository tokenRepository;

    @PostMapping("/generate")
    public ResponseEntity<TokenResponse> generateToken(@Valid @RequestBody TokenRequest request) {
        TokenResponse response = tokenService.generateToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tokenId}")
    public ResponseEntity<TokenDTO> getToken(@PathVariable String tokenId) {
        Token token = tokenService.getTokenById(tokenId);
        return ResponseEntity.ok(TokenDTO.fromEntity(token));
    }

    @GetMapping("/live/serving")
    public ResponseEntity<List<LiveTokenDTO>> getServingTokens() {
        List<LiveTokenDTO> tokens = tokenService.getServingTokens();
        return ResponseEntity.ok(tokens);
    }

    @GetMapping("/now-calling")
    public ResponseEntity<List<LiveTokenDTO>> getLiveTokens() {
        List<LiveTokenDTO> latestCalledTokens = tokenService.getLatestCalledTokens();
        return ResponseEntity.ok(latestCalledTokens);
    }

}