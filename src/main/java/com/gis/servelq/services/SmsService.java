package com.gis.servelq.services;

import com.gis.servelq.configs.SmsOmanConfig;
import com.gis.servelq.dto.SmsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.SmsTemplateCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class SmsService {

    private final AuditLogService auditLogService;
    private final SmsOmanConfig smsOmanConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SmsTemplateService smsTemplateService;


    @Async
    public void sendReceiptSmsAsync(SmsRequest request) {
        sendReceiptSms(request);
    }

    /** Sends the ticket receipt; returns the BatchRefCode, or null when it was not sent. */
    public String sendReceiptSms(SmsRequest request) {
        String language = smsTemplateService.effectiveLanguage(request.getLanguage());
        SmsSendResult result = sendTemplate(SmsTemplateCode.TOKEN_RECEIPT, request.getTo(),
                language, receiptValues(request, language), request.getTokenNo());
        return result.success() ? result.batchRefCode() : null;
    }

    /** Sends a template filled with sample values, so the admin can check the text. */
    public SmsSendResult sendTest(SmsTemplateCode code, String to, String language) {
        Map<String, String> sample = new HashMap<>();
        boolean arabic = "ar".equalsIgnoreCase(language);
        sample.put("token", "A001");
        sample.put("service", arabic ? "استفسار معاش" : "Pension Inquiry");
        sample.put("queue", "5");
        LocalDateTime now = LocalDateTime.now();
        sample.put("date", now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        sample.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm")));
        return sendTemplate(code, to, language, sample, "TEST");
    }

    public SmsSendResult sendTemplate(SmsTemplateCode code, String to, String language,
                                      Map<String, String> values, String sourceRef) {
        if (isBlank(to)) {
            return SmsSendResult.failed("No mobile number");
        }
        if (!Boolean.TRUE.equals(smsTemplateService.settings().getEnabled())) {
            log.info("SMS sending is switched off in the admin panel - not sending {} to {}", code, to);
            return SmsSendResult.failed("SMS sending is switched off");
        }
        String sender = smsTemplateService.effectiveSender();
        if (isBlank(smsOmanConfig.username()) || isBlank(smsOmanConfig.password()) || isBlank(sender)) {
            log.warn("SMS Oman credentials or sender are not configured");
            return SmsSendResult.failed("SMSOMAN username, password or sender is not configured");
        }
        Optional<String> message = smsTemplateService.render(code, language, values);
        if (message.isEmpty()) {
            log.info("SMS template {} is switched off - not sending to {}", code, to);
            return SmsSendResult.failed("This SMS template is switched off");
        }

        String mobileNumber = to.trim();
        try {
            Map<String, Object> payload = buildSmsPayload(message.get(), mobileNumber, sender, sourceRef);

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
                return SmsSendResult.failed("SMSOMAN returned an unreadable response");
            }

            if ("00".equals(body.getStatusCode())) {
                log.info("SMS {} sent successfully to {}. BatchRefCode={}",
                        code, mobileNumber, body.getBatchRefCode());

                auditLogService.log(
                        AuditAction.SMS_SENT,
                        "SMS",
                        body.getBatchRefCode(),
                        mobileNumber,
                        "SMS " + code + " sent to: " + mobileNumber, null, null, null
                );
                return new SmsSendResult(true, body.getBatchRefCode(), body.getStatusCode(), body.getStatusDesc());
            }

            log.error("SMS Oman failed. StatusCode={}, StatusDesc={}, raw response: {}",
                    body.getStatusCode(), body.getStatusDesc(), abbreviate(raw));
            return new SmsSendResult(false, null, body.getStatusCode(),
                    body.getStatusDesc() == null ? null : body.getStatusDesc().trim());

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", mobileNumber, e.getMessage(), e);
            return SmsSendResult.failed("Could not reach SMSOMAN: " + e.getMessage());
        }
    }

    private static Map<String, String> receiptValues(SmsRequest request, String language) {
        boolean arabic = "ar".equalsIgnoreCase(language);
        Map<String, String> values = new HashMap<>();
        values.put("token", request.getTokenNo());
        values.put("service", arabic && !isBlank(request.getServiceAr())
                ? request.getServiceAr() : request.getServiceEn());
        values.put("queue", request.getCurrentQueue());
        values.put("date", request.getDate());
        values.put("time", request.getTime());
        return values;
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

    private Map<String, Object> buildSmsPayload(String message, String mobileNumber,
                                                String sender, String sourceRef) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("UserName", smsOmanConfig.username());
        payload.put("Password", smsOmanConfig.password());
        payload.put("Message", message);
        payload.put("Priority", smsTemplateService.effectivePriority());

        // Empty means send immediately.
        payload.put("Schdate", "");

        payload.put("Sender", sender);
        payload.put("AppID", smsTemplateService.effectiveAppId());

        // 1 to 10 characters according to the API document.
        String ref = isBlank(sourceRef) ? "SMS" : sourceRef.trim();
        payload.put("SourceRef", ref.length() > 10 ? ref.substring(0, 10) : ref);

        payload.put("MSISDNs", mobileNumber);
        return payload;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}