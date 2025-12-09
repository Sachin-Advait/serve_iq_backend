package com.gis.servelq.controllers;

import com.gis.servelq.services.WhatsAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/serveiq/api/whatsapp")
public class WhatsAppController {

    @Autowired
    private WhatsAppService service;

    @GetMapping("/send")
    public String send(@RequestParam String to, @RequestParam String msg) {
        return service.sendMessage(to, msg);
    }
}


//curl -X GET "http://localhost:8085/serveiq/api/whatsapp/send?to=+919717453667&msg=Hello+from+SpringBoot"
