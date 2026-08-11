package com.gis.servelq.controllers;

import com.gis.servelq.dto.WhatsAppRequest;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/serveiq/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService service;

    @PostMapping("/send")
    public String send(@RequestBody WhatsAppRequest request,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        // The user parameter is available here for additional authorization checks if needed
        // Currently, SecurityConfig restricts this endpoint to ADMIN role
        return service.sendMessage(request.getTo(), request.getMsg());
    }
}