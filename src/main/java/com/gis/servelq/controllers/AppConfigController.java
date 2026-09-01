package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.AppDashboardDTO;
import com.gis.servelq.dto.ComponentOrderDTO;
import com.gis.servelq.models.AppConfig;
import com.gis.servelq.models.AppType;
import com.gis.servelq.services.AppConfigService;
import com.gis.servelq.services.SocketService;
import com.gis.servelq.services.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/serveiq/api/app-config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService configService;
    private final TemplateService templateService;
    private final SocketService socketService;

    @Value("${image.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.default.image.dir:DefaultImages}")
    private String defaultImageDir;

    @GetMapping("/{appType}/dashboard")
    public ApiResponseDTO<AppDashboardDTO> getAppDashboard(@PathVariable AppType appType) {
        templateService.getActiveTemplate(appType);
        return new ApiResponseDTO<>(true, "App dashboard fetched",
                configService.getAppDashboard(appType));
    }

    @GetMapping("/{appType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfig> getConfig(@PathVariable AppType appType) {
        templateService.getActiveTemplate(appType);
        return new ApiResponseDTO<>(true, "App config fetched",
                configService.getConfig(appType));
    }

    @PutMapping("/{appType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfig> updateConfig(@PathVariable AppType appType,
                                                  @RequestBody AppConfig config) {
        templateService.updateActiveTemplateConfig(appType, config);
        AppConfig saved = configService.getConfig(appType);
        socketService.broadcastAppDashboard(appType);
        return new ApiResponseDTO<>(true, "Active template updated", saved);
    }

    @PostMapping("/{appType}/reset-default")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfig> resetToDefault(@PathVariable AppType appType) {
        AppConfig reset = configService.resetToDefault(appType);
        templateService.updateActiveTemplateConfig(appType, reset);
        socketService.broadcastAppDashboard(appType);
        return new ApiResponseDTO<>(true, "All settings reset to default", reset);
    }

    @PostMapping("/{appType}/upload-background")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfig> uploadBackgroundImage(@PathVariable AppType appType,
                                                           @RequestParam("file") MultipartFile file) {
        AppConfig updated = configService.uploadBackgroundImage(appType, file);
        templateService.updateActiveTemplateConfig(appType, updated);
        socketService.broadcastAppDashboard(appType);
        return new ApiResponseDTO<>(true, "Background image uploaded", updated);
    }

    @PostMapping("/{appType}/upload-logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfig> uploadAppLogo(@PathVariable AppType appType,
                                                   @RequestParam("file") MultipartFile file) {
        AppConfig updated = configService.uploadAppLogo(appType, file);
        templateService.updateActiveTemplateConfig(appType, updated);
        socketService.broadcastAppDashboard(appType);
        return new ApiResponseDTO<>(true, "App logo uploaded", updated);
    }

    @GetMapping("/{appType}/component-order")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<List<String>> getComponentOrder(@PathVariable AppType appType) {
        AppConfig config = configService.getConfig(appType);
        return new ApiResponseDTO<>(true, "Component order fetched",
                config.getComponentOrder());
    }

    @PutMapping("/{appType}/component-order")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<AppConfig> updateComponentOrder(@PathVariable AppType appType,
                                                          @RequestBody ComponentOrderDTO orderDTO) {
        AppConfig updated = configService.updateComponentOrder(appType, orderDTO);
        templateService.updateActiveTemplateConfig(appType, updated);
        socketService.broadcastAppDashboard(appType);
        return new ApiResponseDTO<>(true, "Component order updated", updated);
    }

    // ==================== IMAGE SERVING ENDPOINTS ====================

    /**
     * Serve image - handles both uploaded and default images
     * Path: /serveiq/api/app-config/images/{type}/{appType}/{fileName}
     *
     * Examples:
     * - /images/backgrounds/tv_display/bg.png
     * - /images/logos/logo/logo.png
     * - /images/logos/tv_display/custom-logo.png
     */
    @GetMapping("/images/{type}/{appType}/{fileName}")
    public ResponseEntity<Resource> getImage(@PathVariable String type,
                                             @PathVariable String appType,
                                             @PathVariable String fileName) {
        try {
            Path filePath = null;

            log.debug("Serving image: type={}, appType={}, fileName={}", type, appType, fileName);

            // Step 1: Try uploads directory
            Path uploadPath = Paths.get(uploadDir, type, appType.toLowerCase(), fileName)
                    .toAbsolutePath().normalize();

            if (Files.exists(uploadPath)) {
                filePath = uploadPath;
                log.debug("Found in uploads: {}", filePath);
            } else {
                // Step 2: Try DefaultImages/{appType} directory
                Path defaultPath = Paths.get(defaultImageDir, appType.toLowerCase(), fileName)
                        .toAbsolutePath().normalize();

                if (Files.exists(defaultPath)) {
                    filePath = defaultPath;
                    log.debug("Found in DefaultImages: {}", filePath);
                } else if ("logo".equalsIgnoreCase(appType) || "logos".equalsIgnoreCase(type)) {
                    // Step 3: For logo, try the shared logo directory
                    Path logoPath = Paths.get(defaultImageDir, "logo", fileName)
                            .toAbsolutePath().normalize();

                    if (Files.exists(logoPath)) {
                        filePath = logoPath;
                        log.debug("Found in shared logo directory: {}", filePath);
                    }
                }
            }

            if (filePath == null || !Files.exists(filePath)) {
                log.warn("Image not found: type={}, appType={}, fileName={}", type, appType, fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = getContentType(fileName);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve image: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Legacy image serving endpoint
     * Path: /serveiq/api/app-config/images/{type}/{fileName}
     */
    @GetMapping("/images/{type}/{fileName}")
    public ResponseEntity<Resource> getImageLegacy(@PathVariable String type,
                                                   @PathVariable String fileName) {
        try {
            Path filePath = null;

            // Try uploads directory first
            Path uploadPath = Paths.get(uploadDir, type, fileName)
                    .toAbsolutePath().normalize();

            if (Files.exists(uploadPath)) {
                filePath = uploadPath;
            } else if ("logos".equals(type) || "logo".equals(type)) {
                // Try shared logo directory
                Path logoPath = Paths.get(defaultImageDir, "logo", fileName)
                        .toAbsolutePath().normalize();
                if (Files.exists(logoPath)) {
                    filePath = logoPath;
                }
            } else {
                // Try other default directories
                Path defaultPath = Paths.get(defaultImageDir, type, fileName)
                        .toAbsolutePath().normalize();
                if (Files.exists(defaultPath)) {
                    filePath = defaultPath;
                }
            }

            if (filePath == null || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = getContentType(fileName);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Helper method to determine content type from file extension
     */
    private String getContentType(String fileName) {
        if (fileName == null) return "image/jpeg";

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".webp")) return "image/webp";
        else if (lower.endsWith(".png")) return "image/png";
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        else if (lower.endsWith(".gif")) return "image/gif";
        else return "image/jpeg";
    }
}