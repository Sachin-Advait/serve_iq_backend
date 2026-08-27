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
        if (request.getTickerEnabled() != null) template.setTickerEnabled(request.getTickerEnabled());
        if (request.getTickerSpeed() != null) template.setTickerSpeed(request.getTickerSpeed());
        if (request.getVideosEnabled() != null) template.setVideosEnabled(request.getVideosEnabled());
        if (request.getFlickerTime() != null) template.setFlickerTime(request.getFlickerTime());
        if (request.getComponentOrder() != null) template.setComponentOrder(new ArrayList<>(request.getComponentOrder()));
        if (request.getImageCarouselEnabled() != null) template.setImageCarouselEnabled(request.getImageCarouselEnabled());
        // FIXED: Changed from getVideoEnabled() to getFeedbackVideoEnabled()
        if (request.getFeedbackVideoEnabled() != null) template.setFeedbackVideoEnabled(request.getFeedbackVideoEnabled());

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
        // FIXED: Changed from getVideoEnabled() to getFeedbackVideoEnabled()
        if (updatedConfig.getFeedbackVideoEnabled() != null)
            activeTemplate.setFeedbackVideoEnabled(updatedConfig.getFeedbackVideoEnabled());

        activeTemplate.setUpdatedAt(LocalDateTime.now());
        AppConfigTemplate saved = templateRepository.save(activeTemplate);

        applyTemplateToConfig(saved);

        return saved;
    }

    @Transactional
    public AppConfigTemplate updateTemplate(String templateId, TemplateRequestDTO request) {
        AppConfigTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("Template not found"));

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
        if (request.getTickerEnabled() != null) template.setTickerEnabled(request.getTickerEnabled());
        if (request.getTickerSpeed() != null) template.setTickerSpeed(request.getTickerSpeed());
        if (request.getVideosEnabled() != null) template.setVideosEnabled(request.getVideosEnabled());
        if (request.getFlickerTime() != null) template.setFlickerTime(request.getFlickerTime());
        if (request.getComponentOrder() != null) template.setComponentOrder(new ArrayList<>(request.getComponentOrder()));
        if (request.getImageCarouselEnabled() != null) template.setImageCarouselEnabled(request.getImageCarouselEnabled());
        // FIXED: Changed from getVideoEnabled() to getFeedbackVideoEnabled()
        if (request.getFeedbackVideoEnabled() != null) template.setFeedbackVideoEnabled(request.getFeedbackVideoEnabled());

        template.setUpdatedAt(LocalDateTime.now());
        AppConfigTemplate saved = templateRepository.save(template);

        if (saved.isActive() || saved.isDefault()) {
            applyTemplateToConfig(saved);
        }

        return saved;
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
        config.setTickerEnabled(template.getTickerEnabled());
        config.setTickerSpeed(template.getTickerSpeed());
        config.setVideosEnabled(template.getVideosEnabled());
        config.setFlickerTime(template.getFlickerTime());

        if (template.getComponentOrder() != null) {
            config.setComponentOrder(new ArrayList<>(template.getComponentOrder()));
        }

        config.setImageCarouselEnabled(template.getImageCarouselEnabled());
        // FIXED: Changed from getVideoEnabled() to getFeedbackVideoEnabled()
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
        template.setTickerEnabled(config.getTickerEnabled());
        template.setTickerSpeed(config.getTickerSpeed());
        template.setVideosEnabled(config.getVideosEnabled());
        template.setFlickerTime(config.getFlickerTime());
        template.setComponentOrder(config.getComponentOrder() != null ?
                new ArrayList<>(config.getComponentOrder()) : null);
        template.setImageCarouselEnabled(config.getImageCarouselEnabled());
        // FIXED: Changed from getVideoEnabled() to getFeedbackVideoEnabled()
        template.setFeedbackVideoEnabled(config.getFeedbackVideoEnabled());
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
        target.setTickerEnabled(source.getTickerEnabled());
        target.setTickerSpeed(source.getTickerSpeed());
        target.setVideosEnabled(source.getVideosEnabled());
        target.setFlickerTime(source.getFlickerTime());
        target.setComponentOrder(source.getComponentOrder() != null ?
                new ArrayList<>(source.getComponentOrder()) : null);
        target.setImageCarouselEnabled(source.getImageCarouselEnabled());
        // FIXED: Changed from getVideoEnabled() to getFeedbackVideoEnabled()
        target.setFeedbackVideoEnabled(source.getFeedbackVideoEnabled());
    }
}