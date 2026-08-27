package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.TemplateRequestDTO;
import com.gis.servelq.models.AppConfig;
import com.gis.servelq.models.AppConfigTemplate;
import com.gis.servelq.models.AppType;
import com.gis.servelq.services.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/serveiq/api/app-config/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/{appType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<List<AppConfigTemplate>> getAllTemplates(@PathVariable AppType appType) {
        return new ApiResponseDTO<>(true, "Templates fetched",
                templateService.getAllTemplates(appType));
    }

    @GetMapping("/{appType}/active")
    public ApiResponseDTO<AppConfigTemplate> getActiveTemplate(@PathVariable AppType appType) {
        return new ApiResponseDTO<>(true, "Active template fetched",
                templateService.getActiveTemplate(appType));
    }

    @PostMapping("/{appType}/from-current")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> createTemplateFromCurrent(
            @PathVariable AppType appType,
            @RequestBody TemplateRequestDTO request) {
        AppConfigTemplate created = templateService.createTemplateFromActive(
                appType,
                request.getTemplateName(),
                request.getDescription()
        );
        return new ApiResponseDTO<>(true, "Template created from current settings", created);
    }

    @PostMapping("/{appType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> createTemplate(
            @PathVariable AppType appType,
            @RequestBody TemplateRequestDTO request) {
        AppConfigTemplate created = templateService.createTemplate(appType, request);
        return new ApiResponseDTO<>(true, "Template created", created);
    }

    @PutMapping("/{appType}/active-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> updateActiveTemplateConfig(
            @PathVariable AppType appType,
            @RequestBody AppConfig config) {
        AppConfigTemplate updated = templateService.updateActiveTemplateConfig(appType, config);
        return new ApiResponseDTO<>(true, "Active template updated", updated);
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> updateTemplate(
            @PathVariable String templateId,
            @RequestBody TemplateRequestDTO request) {
        AppConfigTemplate updated = templateService.updateTemplate(templateId, request);
        return new ApiResponseDTO<>(true, "Template updated", updated);
    }

    @PostMapping("/{templateId}/upload-logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> uploadTemplateLogo(
            @PathVariable String templateId,
            @RequestParam("file") MultipartFile file) {
        AppConfigTemplate updated = templateService.uploadTemplateLogo(templateId, file);
        return new ApiResponseDTO<>(true, "Template logo uploaded", updated);
    }

    @PostMapping("/{templateId}/upload-background")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> uploadTemplateBackground(
            @PathVariable String templateId,
            @RequestParam("file") MultipartFile file) {
        AppConfigTemplate updated = templateService.uploadTemplateBackground(templateId, file);
        return new ApiResponseDTO<>(true, "Template background uploaded", updated);
    }

    @PostMapping("/{templateId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfigTemplate> activateTemplate(@PathVariable String templateId) {
        AppConfigTemplate activated = templateService.activateTemplate(templateId);
        return new ApiResponseDTO<>(true, "Template activated", activated);
    }

    @PostMapping("/{templateId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<Void> deactivateTemplate(@PathVariable String templateId) {
        templateService.deactivateTemplate(templateId);
        return new ApiResponseDTO<>(true, "Template deactivated", null);
    }

    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<Void> deleteTemplate(@PathVariable String templateId) {
        templateService.deleteTemplate(templateId);
        return new ApiResponseDTO<>(true, "Template deleted", null);
    }
}