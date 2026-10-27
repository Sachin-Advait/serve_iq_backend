package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.configs.SmsOmanConfig;
import com.gis.servelq.dto.SmsSettingsDTO;
import com.gis.servelq.dto.SmsTemplateDTO;
import com.gis.servelq.dto.SmsTemplateRequest;
import com.gis.servelq.models.SmsSettings;
import com.gis.servelq.models.SmsTemplate;
import com.gis.servelq.models.SmsTemplateCode;
import com.gis.servelq.repository.SmsSettingsRepository;
import com.gis.servelq.repository.SmsTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The admin-edited SMS text and settings. Secrets (SMSOMAN username,
 * password, URL) stay in the environment; this only holds what the admin
 * may change.
 */
@Service
@RequiredArgsConstructor
public class SmsTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    /** Used if the TOKEN_RECEIPT row is missing; same text as V36 seeds. */
    static final String DEFAULT_RECEIPT_EN = """
            MSSPF
            Visitor Management

            Queue Token: {token}
            Service: {service}
            Current Queue: {queue}
            Date: {date}
            Time: {time}

            Please wait for your number to be called.
            Thank you.""";

    static final String DEFAULT_RECEIPT_AR = """
            صندوق تقاعد الأجهزة العسكرية والأمنية
            نظام إدارة الزوار

            رقم الانتظار: {token}
            الخدمة: {service}
            المنتظرون حالياً: {queue}
            التاريخ: {date}
            الوقت: {time}

            يرجى انتظار نداء الرقم
            شكراً لك""";

    private final SmsTemplateRepository templateRepository;
    private final SmsSettingsRepository settingsRepository;
    private final SmsOmanConfig smsOmanConfig;

    public List<SmsTemplateDTO> findAll() {
        return templateRepository.findAllByOrderByNameAsc().stream()
                .map(t -> SmsTemplateDTO.from(t, placeholdersFor(t.getCode())))
                .toList();
    }

    @Transactional
    public SmsTemplateDTO update(String code, SmsTemplateRequest request) {
        SmsTemplate template = templateRepository.findByCode(normalise(code))
                .orElseThrow(() -> new ResourceNotFoundException("SMS template not found: " + code));
        List<String> allowed = placeholdersFor(template.getCode());
        if (request.getBodyEn() != null) {
            template.setBodyEn(validBody(request.getBodyEn(), allowed, "English"));
        }
        if (request.getBodyAr() != null) {
            template.setBodyAr(validBody(request.getBodyAr(), allowed, "Arabic"));
        }
        if (request.getActive() != null) {
            template.setActive(request.getActive());
        }
        return SmsTemplateDTO.from(templateRepository.save(template), allowed);
    }

    /**
     * The text to send, or empty when the template is switched off. A missing
     * receipt row falls back to the built-in text so receipts keep working.
     */
    public Optional<String> render(SmsTemplateCode code, String language, Map<String, String> values) {
        Optional<SmsTemplate> row = templateRepository.findByCode(code.name());
        if (row.isPresent() && !Boolean.TRUE.equals(row.get().getActive())) {
            return Optional.empty();
        }
        boolean arabic = "ar".equalsIgnoreCase(language);
        String body = row.map(t -> arabic ? t.getBodyAr() : t.getBodyEn())
                .orElseGet(() -> defaultBody(code, arabic));
        return Optional.of(fill(body, values));
    }

    static String fill(String body, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            String value = values.containsKey(key) ? values.get(key) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    // ---- settings ----

    public SmsSettings settings() {
        return settingsRepository.findById(SmsSettings.SINGLETON_ID).orElseGet(SmsSettings::new);
    }

    public SmsSettingsDTO settingsDto() {
        SmsSettings s = settings();
        SmsSettingsDTO dto = new SmsSettingsDTO();
        dto.setEnabled(s.getEnabled());
        dto.setSender(s.getSender());
        dto.setPriority(s.getPriority());
        dto.setAppId(s.getAppId());
        dto.setLanguage(s.getLanguage());
        dto.setDefaultSender(smsOmanConfig.sender());
        dto.setDefaultPriority(smsOmanConfig.priority());
        dto.setDefaultAppId(smsOmanConfig.appId());
        dto.setCredentialsConfigured(!isBlank(smsOmanConfig.username()) && !isBlank(smsOmanConfig.password()));
        return dto;
    }

    @Transactional
    public SmsSettingsDTO updateSettings(SmsSettingsDTO request) {
        SmsSettings s = settings();
        if (request.getEnabled() != null) s.setEnabled(request.getEnabled());
        s.setSender(blankToNull(request.getSender()));
        if (s.getSender() != null && s.getSender().length() > 11) {
            throw new BusinessException("Sender name can be at most 11 characters");
        }
        Integer priority = request.getPriority();
        if (priority != null && (priority < 1 || priority > 10)) {
            throw new BusinessException("Priority must be between 1 and 10");
        }
        s.setPriority(priority);
        s.setAppId(blankToNull(request.getAppId()));
        String language = blankToNull(request.getLanguage());
        if (language != null) {
            language = language.toLowerCase(Locale.ROOT);
            if (!language.equals("en") && !language.equals("ar")) {
                throw new BusinessException("SMS language must be English (en) or Arabic (ar)");
            }
        }
        s.setLanguage(language);
        settingsRepository.save(s);
        return settingsDto();
    }

    /** The admin's chosen SMS language, or the visitor's when none is chosen. */
    public String effectiveLanguage(String visitorLanguage) {
        String saved = settings().getLanguage();
        return !isBlank(saved) ? saved : visitorLanguage;
    }

    public String effectiveSender() {
        String saved = settings().getSender();
        return !isBlank(saved) ? saved : smsOmanConfig.sender();
    }

    public String effectivePriority() {
        Integer saved = settings().getPriority();
        return saved != null ? String.valueOf(saved) : smsOmanConfig.priority();
    }

    public String effectiveAppId() {
        String saved = settings().getAppId();
        return !isBlank(saved) ? saved : smsOmanConfig.appId();
    }

    // ---- helpers ----

    static List<String> placeholdersFor(String code) {
        try {
            return SmsTemplateCode.valueOf(code).placeholders();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    static String validBody(String body, List<String> allowed, String label) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("The " + label + " text cannot be empty");
        }
        Set<String> unknown = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(trimmed);
        while (m.find()) {
            if (!allowed.contains(m.group(1).toLowerCase(Locale.ROOT))) unknown.add("{" + m.group(1) + "}");
        }
        if (!unknown.isEmpty()) {
            throw new BusinessException("Unknown placeholder in the " + label + " text: "
                    + String.join(", ", unknown) + ". Allowed: "
                    + String.join(", ", allowed.stream().map(p -> "{" + p + "}").toList()));
        }
        return trimmed;
    }

    private static String defaultBody(SmsTemplateCode code, boolean arabic) {
        return switch (code) {
            case TOKEN_RECEIPT -> arabic ? DEFAULT_RECEIPT_AR : DEFAULT_RECEIPT_EN;
        };
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
