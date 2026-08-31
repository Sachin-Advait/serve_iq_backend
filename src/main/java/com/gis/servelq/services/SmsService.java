package com.gis.servelq.services;

import com.gis.servelq.configs.SmsOmanConfig;
import com.gis.servelq.dto.SmsRequest;
import com.gis.servelq.models.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class SmsService {

    private final AuditLogService auditLogService;
    private final SmsOmanConfig smsOmanConfig;
    private final RestTemplate restTemplate;


    @Async
    public void sendReceiptSmsAsync(SmsRequest request) {
        sendReceiptSms(request);
    }

    public String sendReceiptSms(SmsRequest request) {
        if (isBlank(smsOmanConfig.username())
            || isBlank(smsOmanConfig.password())
            || isBlank(smsOmanConfig.sender())) {
            log.warn("SMS Oman credentials are not configured");
            return null;
        }


        String message = buildReceiptMessage(request);
        String mobileNumber = request.getTo();

        try {

            Map<String, Object> payload = buildSmsPayload(request, message, mobileNumber);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<SmsResponse> response = restTemplate.postForEntity(smsOmanConfig.url(),
                    entity, SmsResponse.class);

            SmsResponse body = response.getBody();

            if (body == null) {
                log.error("SMS Oman returned empty response");
                return null;
            }

            if ("00".equals(body.getStatusCode())) {
                log.info("SMS sent successfully to {}. BatchRefCode={}",
                        mobileNumber, body.getBatchRefCode());

                auditLogService.log(
                        AuditAction.SMS_SENT,
                        "SMS",
                        body.getBatchRefCode(),
                        mobileNumber,
                        "SMS receipt sent to: " + mobileNumber, null, null, null
                );
                return body.getBatchRefCode();
            }

            log.error("SMS Oman failed. StatusCode={}, StatusDesc={}", body.getStatusCode(),
                    body.getStatusDesc());
            return null;

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", mobileNumber, e.getMessage(), e);
            return null;
        }
    }

    private Map<String, Object> buildSmsPayload(SmsRequest request, String message, String mobileNumber) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("UserName", smsOmanConfig.username());
        payload.put("Password", smsOmanConfig.password());
        payload.put("Message", message);
        payload.put("Priority", smsOmanConfig.priority());

        // Empty means send immediately.
        payload.put("Schdate", "");

        payload.put("Sender", smsOmanConfig.sender());
        payload.put("AppID", smsOmanConfig.appId());

        // Maximum 10 characters according to the API document.
        payload.put("SourceRef", request.getTokenNo());

        payload.put("MSISDNs", mobileNumber);
        return payload;
    }

    /**
     * Creates the SMS text in the backend.
     */
    private String buildReceiptMessage(SmsRequest request) {
        boolean arabic = "ar".equalsIgnoreCase(request.getLanguage());
        if (arabic) {
            return String.format(
                    """
                            MSSPF
                            نظام إدارة الزوار
                            
                            رقم الانتظار: %s
                            الخدمة: %s
                            المنتظرون حالياً: %s
                            التاريخ: %s
                            الوقت: %s
                            
                            يرجى انتظار نداء الرقم
                            شكراً لك
                            """,
                    safe(request.getTokenNo()),
                    safe(request.getServiceAr()),
                    safe(request.getCurrentQueue()),
                    safe(request.getDate()),
                    safe(request.getTime())
            ).trim();

        }

        return String.format(
                """
                        MSSPF
                        Visitor Management
                        
                        Queue Token: %s
                        Service: %s
                        Current Queue: %s
                        Date: %s
                        Time: %s
                        
                        Please wait for your number to be called.
                        Thank you.
                        """,
                safe(request.getTokenNo()),
                safe(request.getServiceEn()),
                safe(request.getCurrentQueue()),
                safe(request.getDate()),
                safe(request.getTime())
        ).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}