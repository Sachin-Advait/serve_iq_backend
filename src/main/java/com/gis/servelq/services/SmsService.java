package com.gis.servelq.services;

import com.gis.servelq.configs.SmsOmanConfig;
import com.gis.servelq.dto.SmsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Iterator;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class SmsService {

    private final AuditLogService auditLogService;
    private final SmsOmanConfig smsOmanConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;


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
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(smsOmanConfig.url(),
                    entity, String.class);

            String raw = response.getBody();
            SmsResponse body = parseResponse(raw);

            if (body == null) {
                log.error("SMS Oman returned an unreadable response: {}", abbreviate(raw));
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

            log.error("SMS Oman failed. StatusCode={}, StatusDesc={}, raw response: {}",
                    body.getStatusCode(), body.getStatusDesc(), abbreviate(raw));
            return null;

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", mobileNumber, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Reads SMSOMAN's reply leniently. The document shows a plain JSON object
     * ({"StatusCode":"00",...}), but the live API has answered 200 with a body
     * our fixed field mapping could not read, so this also accepts a JSON
     * string holding the object, an ASP.NET {"d": ...} wrapper, and any key
     * casing. Returns null when no status code can be found.
     */
    SmsResponse parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            for (int i = 0; i < 3 && node != null; i++) {
                if (node.isTextual()) {
                    node = objectMapper.readTree(node.asText());
                } else if (node.isObject() && field(node, "d") != null && field(node, "StatusCode") == null) {
                    node = field(node, "d");
                } else {
                    break;
                }
            }
            if (node == null || !node.isObject() || field(node, "StatusCode") == null) {
                return null;
            }
            SmsResponse response = new SmsResponse();
            response.setStatusCode(text(field(node, "StatusCode")));
            response.setStatusDesc(text(field(node, "StatusDesc")));
            response.setBatchRefCode(text(field(node, "BatchRefCode")));
            JsonNode processed = field(node, "Proccessed");
            if (processed == null) processed = field(node, "Processed");
            if (processed != null && processed.canConvertToInt()) {
                response.setProcessed(processed.asInt());
            }
            return response;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode field(JsonNode node, String name) {
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String abbreviate(String raw) {
        if (raw == null) return "null";
        return raw.length() > 500 ? raw.substring(0, 500) + "..." : raw;
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