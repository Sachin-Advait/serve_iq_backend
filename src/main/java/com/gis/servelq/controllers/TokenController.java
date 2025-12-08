package com.gis.servelq.controllers;

import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.models.Token;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.services.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {
    private final TokenService tokenService;
    private final TokenRepository tokenRepository;

    @PostMapping("/generate")
    public ResponseEntity<TokenResponseDTO> generateToken(@Valid @RequestBody TokenRequest request) {
        TokenResponseDTO response = tokenService.generateToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tokenId}")
    public ResponseEntity<TokenResponseDTO> getToken(@PathVariable String tokenId) {
        Token token = tokenService.getTokenById(tokenId);
        return ResponseEntity.ok(TokenResponseDTO.fromEntity(token));
    }
}