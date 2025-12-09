package com.gis.servelq.controllers;

import com.gis.servelq.dto.WhatsAppRequest;
import com.gis.servelq.services.WhatsAppService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/serveiq/api/whatsapp")
@AllArgsConstructor
public class WhatsAppController {

    private final WhatsAppService service;

    @PostMapping("/send")
    public String send(@RequestBody WhatsAppRequest request) {
        return service.sendMessage(request.getTo(), request.getMsg());
    }
}
