package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.dto.TemplateRequestDTO;
import com.gis.servelq.models.AppConfig;
import com.gis.servelq.models.AppConfigTemplate;
import com.gis.servelq.models.AppType;
import com.gis.servelq.repository.AppConfigRepository;
import com.gis.servelq.repository.AppConfigTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final AppConfigTemplateRepository templateRepository;
    private final AppConfigRepository configRepository;
    private final AppConfigService configService;

    /**
     * Get template by ID
     */
    @Transactional(readOnly = true)
    public AppConfigTemplate getTemplateById(String templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found with ID: " + templateId));
    }

    @Transactional
    public List<AppConfigTemplate> getAllTemplates(AppType appType) {
        log.info("Getting all templates for app type: {}", appType);

        List<AppConfigTemplate> templates = templateRepository.findByAppTypeOrderByCreatedAtDesc(appType.name());
        log.info("Found {} templates", templates.size());

        if (templates.isEmpty()) {
            log.info("No templates found, creating default template...");
            AppConfigTemplate defaultTemplate = createDefaultTemplateInternal(appType);
            log.info("Default template created with ID: {}", defaultTemplate.getId());
            templates = templateRepository.findByAppTypeOrderByCreatedAtDesc(appType.name());
            log.info("After creation, found {} templates", templates.size());
        }

        templates.forEach(t -> {
            if (t.getComponentOrder() != null) {
                t.getComponentOrder().size();
            }
        });

        return templates;
    }

    @Transactional
    public AppConfigTemplate getActiveTemplate(AppType appType) {
        return templateRepository.findByAppTypeAndIsActiveTrue(appType.name())
                .orElseGet(() -> createDefaultTemplateInternal(appType));
    }

    @Transactional
    public AppConfigTemplate createDefaultTemplate(AppType appType) {
        return createDefaultTemplateInternal(appType);
    }

    private AppConfigTemplate createDefaultTemplateInternal(AppType appType) {
        return templateRepository.findByAppTypeAndIsDefaultTrue(appType.name())
                .orElseGet(() -> {
                    log.info("Creating default template for app type: {}", appType);

                    AppConfig currentConfig = configService.getConfig(appType);
                    log.info("Current config ID: {}", currentConfig.getId());

                    AppConfigTemplate template = new AppConfigTemplate();
                    template.setId(UUID.randomUUID().toString());
                    template.setAppType(appType.name());
                    template.setTemplateName("Default Template");
                    template.setDescription("System default template");
                    template.setActive(true);
                    template.setDefault(true);
                    template.setCreatedAt(LocalDateTime.now());
                    template.setUpdatedAt(LocalDateTime.now());

                    copyConfigToTemplate(currentConfig, template);

                    AppConfigTemplate saved = templateRepository.save(template);
                    log.info("Default template saved with ID: {}", saved.getId());

                    applyTemplateToConfig(saved);

                    return saved;
                });
    }

    @Transactional
    public AppConfigTemplate createTemplateFromActive(AppType appType, String templateName, String description) {
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new BusinessException("Template name is required");
        }

        AppConfigTemplate activeTemplate = getActiveTemplate(appType);

        AppConfigTemplate newTemplate = new AppConfigTemplate();
        newTemplate.setId(UUID.randomUUID().toString());
        newTemplate.setAppType(appType.name());
        newTemplate.setTemplateName(templateName.trim());
        newTemplate.setDescription(description);
        newTemplate.setActive(false);
        newTemplate.setDefault(false);
        newTemplate.setCreatedAt(LocalDateTime.now());
        newTemplate.setUpdatedAt(LocalDateTime.now());

        copyTemplateToTemplate(activeTemplate, newTemplate);

        return templateRepository.save(newTemplate);
    }

    @Transactional
    public AppConfigTemplate createTemplate(AppType appType, TemplateRequestDTO request) {
        if (request.getTemplateName() == null || request.getTemplateName().trim().isEmpty()) {
            throw new BusinessException("Template name is required");
        }

        AppConfigTemplate template = new AppConfigTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setAppType(appType.name());
        template.setTemplateName(request.getTemplateName().trim());
        template.setDescription(request.getDescription());

        AppConfigTemplate activeTemplate = getActiveTemplate(appType);
        copyTemplateToTemplate(activeTemplate, template);

        if (request.getBackgroundImage() != null) template.setBackgroundImage(request.getBackgroundImage());
        if (request.getAppLogo() != null) template.setAppLogo(request.getAppLogo());
        if (request.getBackgroundImageUrl() != null) template.setBackgroundImageUrl(request.getBackgroundImageUrl());
        if (request.getAppLogoUrl() != null) template.setAppLogoUrl(request.getAppLogoUrl());
        if (request.getAppLanguage() != null) template.setAppLanguage(request.getAppLanguage());
        if (request.getDefaultEnabled() != null) template.setDefaultEnabled(request.getDefaultEnabled());
        if (request.getPrimaryColor() != null) template.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) template.setSecondaryColor(request.getSecondaryColor());
        if (request.getForegroundFontColor() != null) template.setForegroundFontColor(request.getForegroundFontColor());
        if (request.getTickerEnabled() != null) template.setTickerEnabled(request.getTickerEnabled());
        if (request.getTickerSpeed() != null) template.setTickerSpeed(request.getTickerSpeed());
        if (request.getVideosEnabled() != null) template.setVideosEnabled(request.getVideosEnabled());
        if (request.getFlickerTime() != null) template.setFlickerTime(request.getFlickerTime());
        if (request.getComponentOrder() != null) template.setComponentOrder(new ArrayList<>(request.getComponentOrder()));
        if (request.getImageCarouselEnabled() != null) template.setImageCarouselEnabled(request.getImageCarouselEnabled());
        if (request.getFeedbackVideoEnabled() != null) template.setFeedbackVideoEnabled(request.getFeedbackVideoEnabled());

        // ==================== KIOSK PRINT FIELDS - BILINGUAL ====================
        if (request.getKioskPrintEnabled() != null) template.setKioskPrintEnabled(request.getKioskPrintEnabled());

        // Header
        if (request.getKioskHeaderTitleEn() != null) template.setKioskHeaderTitleEn(fixEncoding(request.getKioskHeaderTitleEn()));
        if (request.getKioskHeaderTitleAr() != null) template.setKioskHeaderTitleAr(fixEncoding(request.getKioskHeaderTitleAr()));
        if (request.getKioskHeaderSubtitleEn() != null) template.setKioskHeaderSubtitleEn(fixEncoding(request.getKioskHeaderSubtitleEn()));
        if (request.getKioskHeaderSubtitleAr() != null) template.setKioskHeaderSubtitleAr(fixEncoding(request.getKioskHeaderSubtitleAr()));

        // Token
        if (request.getKioskTokenTitleEn() != null) template.setKioskTokenTitleEn(fixEncoding(request.getKioskTokenTitleEn()));
        if (request.getKioskTokenTitleAr() != null) template.setKioskTokenTitleAr(fixEncoding(request.getKioskTokenTitleAr()));

        // Service
        if (request.getKioskServiceLabelEn() != null) template.setKioskServiceLabelEn(fixEncoding(request.getKioskServiceLabelEn()));
        if (request.getKioskServiceLabelAr() != null) template.setKioskServiceLabelAr(fixEncoding(request.getKioskServiceLabelAr()));

        // Date & Time
        if (request.getKioskDateLabelEn() != null) template.setKioskDateLabelEn(fixEncoding(request.getKioskDateLabelEn()));
        if (request.getKioskDateLabelAr() != null) template.setKioskDateLabelAr(fixEncoding(request.getKioskDateLabelAr()));
        if (request.getKioskTimeLabelEn() != null) template.setKioskTimeLabelEn(fixEncoding(request.getKioskTimeLabelEn()));
        if (request.getKioskTimeLabelAr() != null) template.setKioskTimeLabelAr(fixEncoding(request.getKioskTimeLabelAr()));

        // Waiting message
        if (request.getKioskWaitingMessageEn() != null) template.setKioskWaitingMessageEn(fixEncoding(request.getKioskWaitingMessageEn()));
        if (request.getKioskWaitingMessageAr() != null) template.setKioskWaitingMessageAr(fixEncoding(request.getKioskWaitingMessageAr()));

        // Footer
        if (request.getKioskThankYouMessageEn() != null) template.setKioskThankYouMessageEn(fixEncoding(request.getKioskThankYouMessageEn()));
        if (request.getKioskThankYouMessageAr() != null) template.setKioskThankYouMessageAr(fixEncoding(request.getKioskThankYouMessageAr()));

        template.setActive(false);
        template.setDefault(false);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());

        return templateRepository.save(template);
    }

    @Transactional
    public AppConfigTemplate updateActiveTemplateConfig(AppType appType, AppConfig updatedConfig) {
        AppConfigTemplate activeTemplate = getActiveTemplate(appType);

        if (updatedConfig.getBackgroundImage() != null)
            activeTemplate.setBackgroundImage(updatedConfig.getBackgroundImage());
        if (updatedConfig.getAppLogo() != null)
            activeTemplate.setAppLogo(updatedConfig.getAppLogo());
        if (updatedConfig.getBackgroundImageUrl() != null)
            activeTemplate.setBackgroundImageUrl(updatedConfig.getBackgroundImageUrl());
        if (updatedConfig.getAppLogoUrl() != null)
            activeTemplate.setAppLogoUrl(updatedConfig.getAppLogoUrl());
        if (updatedConfig.getAppLanguage() != null)
            activeTemplate.setAppLanguage(updatedConfig.getAppLanguage());
        if (updatedConfig.getDefaultEnabled() != null)
            activeTemplate.setDefaultEnabled(updatedConfig.getDefaultEnabled());
        if (updatedConfig.getPrimaryColor() != null)
            activeTemplate.setPrimaryColor(updatedConfig.getPrimaryColor());
        if (updatedConfig.getSecondaryColor() != null)
            activeTemplate.setSecondaryColor(updatedConfig.getSecondaryColor());
        if (updatedConfig.getForegroundFontColor() != null)
            activeTemplate.setForegroundFontColor(updatedConfig.getForegroundFontColor());
        if (updatedConfig.getTickerEnabled() != null)
            activeTemplate.setTickerEnabled(updatedConfig.getTickerEnabled());
        if (updatedConfig.getTickerSpeed() != null)
            activeTemplate.setTickerSpeed(updatedConfig.getTickerSpeed());
        if (updatedConfig.getVideosEnabled() != null)
            activeTemplate.setVideosEnabled(updatedConfig.getVideosEnabled());
        if (updatedConfig.getFlickerTime() != null)
            activeTemplate.setFlickerTime(updatedConfig.getFlickerTime());
        if (updatedConfig.getComponentOrder() != null)
            activeTemplate.setComponentOrder(new ArrayList<>(updatedConfig.getComponentOrder()));
        if (updatedConfig.getImageCarouselEnabled() != null)
            activeTemplate.setImageCarouselEnabled(updatedConfig.getImageCarouselEnabled());
        if (updatedConfig.getFeedbackVideoEnabled() != null)
            activeTemplate.setFeedbackVideoEnabled(updatedConfig.getFeedbackVideoEnabled());

        // ==================== KIOSK PRINT FIELDS - BILINGUAL ====================
        if (updatedConfig.getKioskPrintEnabled() != null)
            activeTemplate.setKioskPrintEnabled(updatedConfig.getKioskPrintEnabled());

        // Header
        if (updatedConfig.getKioskHeaderTitleEn() != null)
            activeTemplate.setKioskHeaderTitleEn(fixEncoding(updatedConfig.getKioskHeaderTitleEn()));
        if (updatedConfig.getKioskHeaderTitleAr() != null)
            activeTemplate.setKioskHeaderTitleAr(fixEncoding(updatedConfig.getKioskHeaderTitleAr()));
        if (updatedConfig.getKioskHeaderSubtitleEn() != null)
            activeTemplate.setKioskHeaderSubtitleEn(fixEncoding(updatedConfig.getKioskHeaderSubtitleEn()));
        if (updatedConfig.getKioskHeaderSubtitleAr() != null)
            activeTemplate.setKioskHeaderSubtitleAr(fixEncoding(updatedConfig.getKioskHeaderSubtitleAr()));

        // Token
        if (updatedConfig.getKioskTokenTitleEn() != null)
            activeTemplate.setKioskTokenTitleEn(fixEncoding(updatedConfig.getKioskTokenTitleEn()));
        if (updatedConfig.getKioskTokenTitleAr() != null)
            activeTemplate.setKioskTokenTitleAr(fixEncoding(updatedConfig.getKioskTokenTitleAr()));

        // Service
        if (updatedConfig.getKioskServiceLabelEn() != null)
            activeTemplate.setKioskServiceLabelEn(fixEncoding(updatedConfig.getKioskServiceLabelEn()));
        if (updatedConfig.getKioskServiceLabelAr() != null)
            activeTemplate.setKioskServiceLabelAr(fixEncoding(updatedConfig.getKioskServiceLabelAr()));

        // Date & Time
        if (updatedConfig.getKioskDateLabelEn() != null)
            activeTemplate.setKioskDateLabelEn(fixEncoding(updatedConfig.getKioskDateLabelEn()));
        if (updatedConfig.getKioskDateLabelAr() != null)
            activeTemplate.setKioskDateLabelAr(fixEncoding(updatedConfig.getKioskDateLabelAr()));
        if (updatedConfig.getKioskTimeLabelEn() != null)
            activeTemplate.setKioskTimeLabelEn(fixEncoding(updatedConfig.getKioskTimeLabelEn()));
        if (updatedConfig.getKioskTimeLabelAr() != null)
            activeTemplate.setKioskTimeLabelAr(fixEncoding(updatedConfig.getKioskTimeLabelAr()));

        // Waiting message
        if (updatedConfig.getKioskWaitingMessageEn() != null)
            activeTemplate.setKioskWaitingMessageEn(fixEncoding(updatedConfig.getKioskWaitingMessageEn()));
        if (updatedConfig.getKioskWaitingMessageAr() != null)
            activeTemplate.setKioskWaitingMessageAr(fixEncoding(updatedConfig.getKioskWaitingMessageAr()));

        // Footer
        if (updatedConfig.getKioskThankYouMessageEn() != null)
            activeTemplate.setKioskThankYouMessageEn(fixEncoding(updatedConfig.getKioskThankYouMessageEn()));
        if (updatedConfig.getKioskThankYouMessageAr() != null)
            activeTemplate.setKioskThankYouMessageAr(fixEncoding(updatedConfig.getKioskThankYouMessageAr()));

        activeTemplate.setUpdatedAt(LocalDateTime.now());
        AppConfigTemplate saved = templateRepository.save(activeTemplate);

        applyTemplateToConfig(saved);

        return saved;
    }

    @Transactional
    public AppConfigTemplate updateTemplate(String templateId, TemplateRequestDTO request) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

        // DEBUG LOGGING
        log.info("🔍 RECEIVED kioskHeaderTitleEn: [{}]", request.getKioskHeaderTitleEn());
        log.info("🔍 RECEIVED kioskHeaderTitleAr: [{}]", request.getKioskHeaderTitleAr());

        if (request.getTemplateName() != null) template.setTemplateName(request.getTemplateName());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getBackgroundImage() != null) template.setBackgroundImage(request.getBackgroundImage());
        if (request.getAppLogo() != null) template.setAppLogo(request.getAppLogo());
        if (request.getBackgroundImageUrl() != null) template.setBackgroundImageUrl(request.getBackgroundImageUrl());
        if (request.getAppLogoUrl() != null) template.setAppLogoUrl(request.getAppLogoUrl());
        if (request.getAppLanguage() != null) template.setAppLanguage(request.getAppLanguage());
        if (request.getDefaultEnabled() != null) template.setDefaultEnabled(request.getDefaultEnabled());
        if (request.getPrimaryColor() != null) template.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) template.setSecondaryColor(request.getSecondaryColor());
        if (request.getForegroundFontColor() != null) template.setForegroundFontColor(request.getForegroundFontColor());
        if (request.getTickerEnabled() != null) template.setTickerEnabled(request.getTickerEnabled());
        if (request.getTickerSpeed() != null) template.setTickerSpeed(request.getTickerSpeed());
        if (request.getVideosEnabled() != null) template.setVideosEnabled(request.getVideosEnabled());
        if (request.getFlickerTime() != null) template.setFlickerTime(request.getFlickerTime());
        if (request.getComponentOrder() != null) template.setComponentOrder(new ArrayList<>(request.getComponentOrder()));
        if (request.getImageCarouselEnabled() != null) template.setImageCarouselEnabled(request.getImageCarouselEnabled());
        if (request.getFeedbackVideoEnabled() != null) template.setFeedbackVideoEnabled(request.getFeedbackVideoEnabled());

        // ==================== KIOSK PRINT FIELDS - BILINGUAL ====================
        if (request.getKioskPrintEnabled() != null) template.setKioskPrintEnabled(request.getKioskPrintEnabled());

        // Header
        if (request.getKioskHeaderTitleEn() != null) template.setKioskHeaderTitleEn(fixEncoding(request.getKioskHeaderTitleEn()));
        if (request.getKioskHeaderTitleAr() != null) template.setKioskHeaderTitleAr(fixEncoding(request.getKioskHeaderTitleAr()));
        if (request.getKioskHeaderSubtitleEn() != null) template.setKioskHeaderSubtitleEn(fixEncoding(request.getKioskHeaderSubtitleEn()));
        if (request.getKioskHeaderSubtitleAr() != null) template.setKioskHeaderSubtitleAr(fixEncoding(request.getKioskHeaderSubtitleAr()));

        // Token
        if (request.getKioskTokenTitleEn() != null) template.setKioskTokenTitleEn(fixEncoding(request.getKioskTokenTitleEn()));
        if (request.getKioskTokenTitleAr() != null) template.setKioskTokenTitleAr(fixEncoding(request.getKioskTokenTitleAr()));

        // Service
        if (request.getKioskServiceLabelEn() != null) template.setKioskServiceLabelEn(fixEncoding(request.getKioskServiceLabelEn()));
        if (request.getKioskServiceLabelAr() != null) template.setKioskServiceLabelAr(fixEncoding(request.getKioskServiceLabelAr()));

        // Date & Time
        if (request.getKioskDateLabelEn() != null) template.setKioskDateLabelEn(fixEncoding(request.getKioskDateLabelEn()));
        if (request.getKioskDateLabelAr() != null) template.setKioskDateLabelAr(fixEncoding(request.getKioskDateLabelAr()));
        if (request.getKioskTimeLabelEn() != null) template.setKioskTimeLabelEn(fixEncoding(request.getKioskTimeLabelEn()));
        if (request.getKioskTimeLabelAr() != null) template.setKioskTimeLabelAr(fixEncoding(request.getKioskTimeLabelAr()));

        // Waiting message
        if (request.getKioskWaitingMessageEn() != null) template.setKioskWaitingMessageEn(fixEncoding(request.getKioskWaitingMessageEn()));
        if (request.getKioskWaitingMessageAr() != null) template.setKioskWaitingMessageAr(fixEncoding(request.getKioskWaitingMessageAr()));

        // Footer
        if (request.getKioskThankYouMessageEn() != null) template.setKioskThankYouMessageEn(fixEncoding(request.getKioskThankYouMessageEn()));
        if (request.getKioskThankYouMessageAr() != null) template.setKioskThankYouMessageAr(fixEncoding(request.getKioskThankYouMessageAr()));

        template.setUpdatedAt(LocalDateTime.now());
        AppConfigTemplate saved = templateRepository.save(template);

        if (saved.isActive() || saved.isDefault()) {
            applyTemplateToConfig(saved);
        }

        return saved;
    }

    /**
     * Fix Arabic text encoding - converts from ISO-8859-1 back to UTF-8 if garbled
     */
    private String fixEncoding(String text) {
        if (text == null || text.isEmpty()) return text;

        // Log for debugging
        log.info("🔧 Encoding check - Original: [{}]", text);

        // Check if text contains garbled characters
        if (text.contains("Ù") || text.contains("Ø") || text.contains("Â") || text.contains("Ã")) {
            try {
                String fixed = new String(text.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                log.info("🔧 Encoding fixed - From: [{}] To: [{}]", text, fixed);
                return fixed;
            } catch (Exception e) {
                log.warn("Failed to fix encoding: {}", e.getMessage());
            }
        } else {
            log.info("✅ Encoding OK - Text is already correct: [{}]", text);
        }

        return text;
    }

    @Transactional
    public AppConfigTemplate uploadTemplateLogo(String templateId, MultipartFile file) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

        AppType appType = AppType.valueOf(template.getAppType());
        AppConfig uploaded = configService.uploadAppLogo(appType, file);

        template.setAppLogo(uploaded.getAppLogo());
        template.setAppLogoUrl(uploaded.getAppLogoUrl());
        template.setUpdatedAt(LocalDateTime.now());

        AppConfigTemplate saved = templateRepository.save(template);

        if (saved.isActive() || saved.isDefault()) {
            applyTemplateToConfig(saved);
        }

        return saved;
    }

    @Transactional
    public AppConfigTemplate uploadTemplateBackground(String templateId, MultipartFile file) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

        AppType appType = AppType.valueOf(template.getAppType());
        AppConfig uploaded = configService.uploadBackgroundImage(appType, file);

        template.setBackgroundImage(uploaded.getBackgroundImage());
        template.setBackgroundImageUrl(uploaded.getBackgroundImageUrl());
        template.setUpdatedAt(LocalDateTime.now());

        AppConfigTemplate saved = templateRepository.save(template);

        if (saved.isActive() || saved.isDefault()) {
            applyTemplateToConfig(saved);
        }

        return saved;
    }

    @Transactional
    public AppConfigTemplate activateTemplate(String templateId) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

        AppType appType = AppType.valueOf(template.getAppType());

        List<AppConfigTemplate> templates = templateRepository.findByAppTypeOrderByCreatedAtDesc(template.getAppType());
        for (AppConfigTemplate t : templates) {
            t.setActive(false);
            templateRepository.save(t);
        }

        template.setActive(true);
        template.setUpdatedAt(LocalDateTime.now());
        AppConfigTemplate saved = templateRepository.save(template);

        applyTemplateToConfig(saved);

        return saved;
    }

    @Transactional
    public void deactivateTemplate(String templateId) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

        if (template.isDefault()) {
            throw new BusinessException("Cannot deactivate default template");
        }

        template.setActive(false);
        template.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);

        AppType appType = AppType.valueOf(template.getAppType());
        if (templateRepository.countByAppTypeAndIsActiveTrue(template.getAppType()) == 0) {
            activateDefaultTemplate(appType);
        }
    }

    @Transactional
    public void deleteTemplate(String templateId) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

        if (template.isDefault()) {
            throw new BusinessException("Cannot delete default template");
        }

        if (template.isActive()) {
            AppType appType = AppType.valueOf(template.getAppType());
            templateRepository.delete(template);
            activateDefaultTemplate(appType);
        } else {
            templateRepository.delete(template);
        }
    }

    @Transactional
    public AppConfigTemplate activateDefaultTemplate(AppType appType) {
        return templateRepository.findByAppTypeAndIsDefaultTrue(appType.name())
                .map(template -> {
                    List<AppConfigTemplate> templates = templateRepository.findByAppTypeOrderByCreatedAtDesc(appType.name());
                    for (AppConfigTemplate t : templates) {
                        t.setActive(false);
                        templateRepository.save(t);
                    }

                    template.setActive(true);
                    template.setUpdatedAt(LocalDateTime.now());
                    AppConfigTemplate saved = templateRepository.save(template);

                    applyTemplateToConfig(saved);

                    return saved;
                })
                .orElseGet(() -> createDefaultTemplateInternal(appType));
    }

    private void applyTemplateToConfig(AppConfigTemplate template) {
        AppType appType = AppType.valueOf(template.getAppType());
        AppConfig config = configService.getConfig(appType);

        config.setBackgroundImage(template.getBackgroundImage());
        config.setAppLogo(template.getAppLogo());
        config.setBackgroundImageUrl(template.getBackgroundImageUrl());
        config.setAppLogoUrl(template.getAppLogoUrl());
        config.setAppLanguage(template.getAppLanguage());
        config.setDefaultEnabled(template.getDefaultEnabled());
        config.setPrimaryColor(template.getPrimaryColor());
        config.setSecondaryColor(template.getSecondaryColor());
        config.setForegroundFontColor(template.getForegroundFontColor());
        config.setTickerEnabled(template.getTickerEnabled());
        config.setTickerSpeed(template.getTickerSpeed());
        config.setVideosEnabled(template.getVideosEnabled());
        config.setFlickerTime(template.getFlickerTime());

        // ==================== KIOSK PRINT FIELDS - BILINGUAL ====================
        config.setKioskPrintEnabled(template.getKioskPrintEnabled());

        // Header
        config.setKioskHeaderTitleEn(template.getKioskHeaderTitleEn());
        config.setKioskHeaderTitleAr(template.getKioskHeaderTitleAr());
        config.setKioskHeaderSubtitleEn(template.getKioskHeaderSubtitleEn());
        config.setKioskHeaderSubtitleAr(template.getKioskHeaderSubtitleAr());

        // Token
        config.setKioskTokenTitleEn(template.getKioskTokenTitleEn());
        config.setKioskTokenTitleAr(template.getKioskTokenTitleAr());

        // Service
        config.setKioskServiceLabelEn(template.getKioskServiceLabelEn());
        config.setKioskServiceLabelAr(template.getKioskServiceLabelAr());

        // Date & Time
        config.setKioskDateLabelEn(template.getKioskDateLabelEn());
        config.setKioskDateLabelAr(template.getKioskDateLabelAr());
        config.setKioskTimeLabelEn(template.getKioskTimeLabelEn());
        config.setKioskTimeLabelAr(template.getKioskTimeLabelAr());

        // Waiting message
        config.setKioskWaitingMessageEn(template.getKioskWaitingMessageEn());
        config.setKioskWaitingMessageAr(template.getKioskWaitingMessageAr());

        // Footer
        config.setKioskThankYouMessageEn(template.getKioskThankYouMessageEn());
        config.setKioskThankYouMessageAr(template.getKioskThankYouMessageAr());

        if (template.getComponentOrder() != null) {
            config.setComponentOrder(new ArrayList<>(template.getComponentOrder()));
        }

        config.setImageCarouselEnabled(template.getImageCarouselEnabled());
        config.setFeedbackVideoEnabled(template.getFeedbackVideoEnabled());

        config.setDefaultEnabled(template.isDefault());

        configRepository.save(config);
    }

    private void copyConfigToTemplate(AppConfig config, AppConfigTemplate template) {
        template.setBackgroundImage(config.getBackgroundImage());
        template.setAppLogo(config.getAppLogo());
        template.setBackgroundImageUrl(config.getBackgroundImageUrl());
        template.setAppLogoUrl(config.getAppLogoUrl());
        template.setAppLanguage(config.getAppLanguage());
        template.setDefaultEnabled(config.getDefaultEnabled());
        template.setPrimaryColor(config.getPrimaryColor());
        template.setSecondaryColor(config.getSecondaryColor());
        template.setForegroundFontColor(config.getForegroundFontColor());
        template.setTickerEnabled(config.getTickerEnabled());
        template.setTickerSpeed(config.getTickerSpeed());
        template.setVideosEnabled(config.getVideosEnabled());
        template.setFlickerTime(config.getFlickerTime());
        template.setComponentOrder(config.getComponentOrder() != null ?
                new ArrayList<>(config.getComponentOrder()) : null);
        template.setImageCarouselEnabled(config.getImageCarouselEnabled());
        template.setFeedbackVideoEnabled(config.getFeedbackVideoEnabled());

        // ==================== KIOSK PRINT FIELDS - BILINGUAL ====================
        template.setKioskPrintEnabled(config.getKioskPrintEnabled());

        // Header
        template.setKioskHeaderTitleEn(config.getKioskHeaderTitleEn());
        template.setKioskHeaderTitleAr(config.getKioskHeaderTitleAr());
        template.setKioskHeaderSubtitleEn(config.getKioskHeaderSubtitleEn());
        template.setKioskHeaderSubtitleAr(config.getKioskHeaderSubtitleAr());

        // Token
        template.setKioskTokenTitleEn(config.getKioskTokenTitleEn());
        template.setKioskTokenTitleAr(config.getKioskTokenTitleAr());

        // Service
        template.setKioskServiceLabelEn(config.getKioskServiceLabelEn());
        template.setKioskServiceLabelAr(config.getKioskServiceLabelAr());

        // Date & Time
        template.setKioskDateLabelEn(config.getKioskDateLabelEn());
        template.setKioskDateLabelAr(config.getKioskDateLabelAr());
        template.setKioskTimeLabelEn(config.getKioskTimeLabelEn());
        template.setKioskTimeLabelAr(config.getKioskTimeLabelAr());

        // Waiting message
        template.setKioskWaitingMessageEn(config.getKioskWaitingMessageEn());
        template.setKioskWaitingMessageAr(config.getKioskWaitingMessageAr());

        // Footer
        template.setKioskThankYouMessageEn(config.getKioskThankYouMessageEn());
        template.setKioskThankYouMessageAr(config.getKioskThankYouMessageAr());
    }

    private void copyTemplateToTemplate(AppConfigTemplate source, AppConfigTemplate target) {
        target.setBackgroundImage(source.getBackgroundImage());
        target.setAppLogo(source.getAppLogo());
        target.setBackgroundImageUrl(source.getBackgroundImageUrl());
        target.setAppLogoUrl(source.getAppLogoUrl());
        target.setAppLanguage(source.getAppLanguage());
        target.setDefaultEnabled(source.getDefaultEnabled());
        target.setPrimaryColor(source.getPrimaryColor());
        target.setSecondaryColor(source.getSecondaryColor());
        target.setForegroundFontColor(source.getForegroundFontColor());
        target.setTickerEnabled(source.getTickerEnabled());
        target.setTickerSpeed(source.getTickerSpeed());
        target.setVideosEnabled(source.getVideosEnabled());
        target.setFlickerTime(source.getFlickerTime());
        target.setComponentOrder(source.getComponentOrder() != null ?
                new ArrayList<>(source.getComponentOrder()) : null);
        target.setImageCarouselEnabled(source.getImageCarouselEnabled());
        target.setFeedbackVideoEnabled(source.getFeedbackVideoEnabled());

        // ==================== KIOSK PRINT FIELDS - BILINGUAL ====================
        target.setKioskPrintEnabled(source.getKioskPrintEnabled());

        // Header
        target.setKioskHeaderTitleEn(source.getKioskHeaderTitleEn());
        target.setKioskHeaderTitleAr(source.getKioskHeaderTitleAr());
        target.setKioskHeaderSubtitleEn(source.getKioskHeaderSubtitleEn());
        target.setKioskHeaderSubtitleAr(source.getKioskHeaderSubtitleAr());

        // Token
        target.setKioskTokenTitleEn(source.getKioskTokenTitleEn());
        target.setKioskTokenTitleAr(source.getKioskTokenTitleAr());

        // Service
        target.setKioskServiceLabelEn(source.getKioskServiceLabelEn());
        target.setKioskServiceLabelAr(source.getKioskServiceLabelAr());

        // Date & Time
        target.setKioskDateLabelEn(source.getKioskDateLabelEn());
        target.setKioskDateLabelAr(source.getKioskDateLabelAr());
        target.setKioskTimeLabelEn(source.getKioskTimeLabelEn());
        target.setKioskTimeLabelAr(source.getKioskTimeLabelAr());

        // Waiting message
        target.setKioskWaitingMessageEn(source.getKioskWaitingMessageEn());
        target.setKioskWaitingMessageAr(source.getKioskWaitingMessageAr());

        // Footer
        target.setKioskThankYouMessageEn(source.getKioskThankYouMessageEn());
        target.setKioskThankYouMessageAr(source.getKioskThankYouMessageAr());
    }
}