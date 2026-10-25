package com.gis.servelq.controllers;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.dto.SmsSettingsDTO;
import com.gis.servelq.dto.SmsTemplateDTO;
import com.gis.servelq.dto.SmsTemplateRequest;
import com.gis.servelq.dto.SmsTestRequest;
import com.gis.servelq.models.SmsTemplateCode;
import com.gis.servelq.services.SmsSendResult;
import com.gis.servelq.services.SmsService;
import com.gis.servelq.services.SmsTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * SMS templates and settings for the admin panel. ADMIN/MANAGER only via the
 * /serveiq/api/admin/** rule in SecurityConfig.
 */
@RestController
@RequestMapping("/serveiq/api/admin/sms")
@RequiredArgsConstructor
public class SmsAdminController {

    private final SmsTemplateService smsTemplateService;
    private final SmsService smsService;

    @GetMapping("/templates")
    public ResponseEntity<List<SmsTemplateDTO>> templates() {
        return ResponseEntity.ok(smsTemplateService.findAll());
    }

    @PutMapping("/templates/{code}")
    public ResponseEntity<SmsTemplateDTO> updateTemplate(@PathVariable String code,
                                                         @RequestBody SmsTemplateRequest request) {
        return ResponseEntity.ok(smsTemplateService.update(code, request));
    }

    @GetMapping("/settings")
    public ResponseEntity<SmsSettingsDTO> settings() {
        return ResponseEntity.ok(smsTemplateService.settingsDto());
    }

    @PutMapping("/settings")
    public ResponseEntity<SmsSettingsDTO> updateSettings(@RequestBody SmsSettingsDTO request) {
        return ResponseEntity.ok(smsTemplateService.updateSettings(request));
    }

    @PostMapping("/test")
    public ResponseEntity<SmsSendResult> test(@RequestBody SmsTestRequest request) {
        SmsTemplateCode code;
        try {
            code = SmsTemplateCode.valueOf(request.getCode() == null
                    ? "" : request.getCode().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown SMS template: " + request.getCode());
        }
        return ResponseEntity.ok(smsService.sendTest(code, request.getTo(), request.getLanguage()));
    }
}
