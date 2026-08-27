package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.dto.AppDashboardDTO;
import com.gis.servelq.dto.ComponentOrderDTO;
import com.gis.servelq.models.AppConfig;
import com.gis.servelq.models.AppType;
import com.gis.servelq.models.BreakingNews;
import com.gis.servelq.models.TvContent;
import com.gis.servelq.repository.AppConfigRepository;
import com.gis.servelq.repository.BreakingNewsRepository;
import com.gis.servelq.repository.TvContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppConfigService {

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".gif"};

    private final AppConfigRepository configRepository;
    private final BreakingNewsRepository newsRepository;
    private final TvContentRepository tvContentRepository;

    @Value("${image.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.default.image.dir:DefaultImages}")
    private String defaultImageDir;

    @Value("${app.base-url:http://localhost:8085}")
    private String appBaseUrl;

    public AppConfig getConfig(AppType appType) {
        return configRepository.findByAppType(appType.name())
                .orElseGet(() -> createDefaultConfig(appType));
    }

    private AppConfig createDefaultConfig(AppType appType) {
        AppConfig config = new AppConfig();
        config.setId(UUID.randomUUID().toString());
        config.setAppType(appType.name());
        config.setAppLanguage("ENGLISH");
        config.setPrimaryColor("#3E321A");
        config.setSecondaryColor("#9E9B46");
        config.setDefaultEnabled(true);

        String logoUrl = getDefaultLogoUrl();
        config.setAppLogoUrl(logoUrl);
        config.setAppLogo(logoUrl);

        switch (appType) {
            case TV_DISPLAY -> {
                config.setTickerEnabled(true);
                config.setTickerSpeed(30);
                config.setVideosEnabled(true);
                config.setFlickerTime(5);
                config.setComponentOrder(new ArrayList<>(List.of("ROOMS", "COUNTERS", "VIDEOS", "TICKER")));
                setDefaultBackgroundImage(config, appType);
            }
            case FEEDBACK -> {
                config.setImageCarouselEnabled(true);
                config.setFeedbackVideoEnabled(false);
                config.setFlickerTime(5);
                setDefaultBackgroundImage(config, appType);
            }
            case KIOSK -> {
                setDefaultBackgroundImage(config, appType);
            }
        }

        return configRepository.save(config);
    }

    public AppConfig saveConfig(AppType appType, AppConfig updated) {
        AppConfig config = getConfig(appType);
        boolean hasChanges = false;

        if (updated.getBackgroundImage() != null) {
            config.setBackgroundImage(updated.getBackgroundImage());
            hasChanges = true;
        }
        if (updated.getAppLogo() != null) {
            config.setAppLogo(updated.getAppLogo());
            hasChanges = true;
        }
        if (updated.getBackgroundImageUrl() != null) {
            config.setBackgroundImageUrl(updated.getBackgroundImageUrl());
            hasChanges = true;
        }
        if (updated.getAppLogoUrl() != null) {
            config.setAppLogoUrl(updated.getAppLogoUrl());
            hasChanges = true;
        }
        if (updated.getAppLanguage() != null) {
            config.setAppLanguage(updated.getAppLanguage());
            hasChanges = true;
        }
        if (updated.getPrimaryColor() != null) {
            config.setPrimaryColor(updated.getPrimaryColor());
            hasChanges = true;
        }
        if (updated.getSecondaryColor() != null) {
            config.setSecondaryColor(updated.getSecondaryColor());
            hasChanges = true;
        }

        switch (appType) {
            case TV_DISPLAY -> {
                if (updated.getTickerEnabled() != null) {
                    config.setTickerEnabled(updated.getTickerEnabled());
                    hasChanges = true;
                }
                if (updated.getVideosEnabled() != null) {
                    config.setVideosEnabled(updated.getVideosEnabled());
                    hasChanges = true;
                }
                if (updated.getTickerSpeed() != null) {
                    config.setTickerSpeed(updated.getTickerSpeed());
                    hasChanges = true;
                }
                if (updated.getComponentOrder() != null) {
                    config.setComponentOrder(new ArrayList<>(updated.getComponentOrder()));
                    hasChanges = true;
                }
                if (updated.getFlickerTime() != null) {
                    config.setFlickerTime(updated.getFlickerTime());
                    hasChanges = true;
                }
            }
            case FEEDBACK -> {
                Boolean newImageCarousel = updated.getImageCarouselEnabled();
                Boolean newFeedbackVideo = updated.getFeedbackVideoEnabled();

                if (newImageCarousel != null && newFeedbackVideo != null) {
                    if (newImageCarousel && newFeedbackVideo) {
                        throw new BusinessException("Image Carousel and Video cannot both be enabled. Please choose one.");
                    }
                    config.setImageCarouselEnabled(newImageCarousel);
                    config.setFeedbackVideoEnabled(newFeedbackVideo);
                    hasChanges = true;
                } else if (newImageCarousel != null) {
                    config.setImageCarouselEnabled(newImageCarousel);
                    if (newImageCarousel) {
                        config.setFeedbackVideoEnabled(false);
                    }
                    hasChanges = true;
                } else if (newFeedbackVideo != null) {
                    config.setFeedbackVideoEnabled(newFeedbackVideo);
                    if (newFeedbackVideo) {
                        config.setImageCarouselEnabled(false);
                    }
                    hasChanges = true;
                }

                if (updated.getFlickerTime() != null) {
                    config.setFlickerTime(updated.getFlickerTime());
                    hasChanges = true;
                }
            }
            case KIOSK -> {
                // Kiosk only has common fields
            }
        }

        if (updated.getDefaultEnabled() != null) {
            config.setDefaultEnabled(updated.getDefaultEnabled());
            if (Boolean.TRUE.equals(updated.getDefaultEnabled())) {
                setDefaultBackgroundImage(config, appType);
            }
        } else if (hasChanges) {
            config.setDefaultEnabled(false);
        }

        return configRepository.save(config);
    }

    public AppConfig updateFeedbackMediaMode(AppType appType, String mode) {
        if (appType != AppType.FEEDBACK) {
            throw new BusinessException("Media mode is only available for FEEDBACK");
        }

        AppConfig config = getConfig(appType);

        switch (mode.toUpperCase()) {
            case "IMAGE_CAROUSEL", "IMAGES", "CAROUSEL" -> {
                config.setImageCarouselEnabled(true);
                config.setFeedbackVideoEnabled(false);
                log.info("Feedback mode set to: IMAGE_CAROUSEL");
            }
            case "VIDEO" -> {
                config.setFeedbackVideoEnabled(true);
                config.setImageCarouselEnabled(false);
                log.info("Feedback mode set to: VIDEO");
            }
            case "NONE", "OFF" -> {
                config.setImageCarouselEnabled(false);
                config.setFeedbackVideoEnabled(false);
                log.info("Feedback mode set to: NONE");
            }
            default -> throw new BusinessException("Invalid media mode: " + mode);
        }

        config.setDefaultEnabled(false);
        return configRepository.save(config);
    }

    private String getDefaultLogoUrl() {
        Path logoDir = Paths.get(defaultImageDir, "logo").toAbsolutePath().normalize();
        Path logoFile = findImageFile(logoDir, "logo");

        if (logoFile != null) {
            String fileName = logoFile.getFileName().toString();
            return appBaseUrl + "/serveiq/api/app-config/images/logos/logo/" + fileName;
        }

        log.warn("No logo image found in: {}", logoDir);
        return appBaseUrl + "/serveiq/api/app-config/images/logos/logo/logo.png";
    }

    private Path findImageFile(Path directory, String baseName) {
        if (!Files.exists(directory)) {
            log.warn("Directory does not exist: {}", directory);
            return null;
        }

        for (String ext : IMAGE_EXTENSIONS) {
            Path file = directory.resolve(baseName + ext);
            if (Files.exists(file)) {
                return file;
            }
        }

        try {
            return Files.list(directory)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") ||
                                name.endsWith(".jpeg") || name.endsWith(".webp") ||
                                name.endsWith(".gif");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to list directory: {}", directory);
            return null;
        }
    }

    private void setDefaultBackgroundImage(AppConfig config, AppType appType) {
        if (config.getBackgroundImage() != null &&
                !config.getBackgroundImage().startsWith("http") &&
                !config.getBackgroundImage().isEmpty()) {
            deleteOldImage(config.getBackgroundImage());
        }

        Path bgDir = Paths.get(defaultImageDir, appType.name().toLowerCase()).toAbsolutePath().normalize();
        Path bgFile = findImageFile(bgDir, "bg");

        if (bgFile != null) {
            String fileName = bgFile.getFileName().toString();
            String url = appBaseUrl + "/serveiq/api/app-config/images/backgrounds/" +
                    appType.name().toLowerCase() + "/" + fileName;

            config.setBackgroundImage(url);
            config.setBackgroundImageUrl(url);
            log.info("Default background set: {}", url);
        } else {
            config.setBackgroundImage(null);
            config.setBackgroundImageUrl(null);
            log.warn("No default background image found for app type: {} in directory: {}", appType, bgDir);
        }

        config.setDefaultEnabled(true);
    }

    public AppConfig uploadBackgroundImage(AppType appType, MultipartFile file) {
        validateImage(file);

        try {
            AppConfig config = getConfig(appType);
            config.setDefaultEnabled(false);

            if (config.getBackgroundImage() != null &&
                    !config.getBackgroundImage().startsWith("http") &&
                    !config.getBackgroundImage().isEmpty()) {
                deleteOldImage(config.getBackgroundImage());
            }

            Path backgroundDir = Paths.get(uploadDir, "backgrounds", appType.name().toLowerCase())
                    .toAbsolutePath().normalize();
            Files.createDirectories(backgroundDir);

            String extension = getExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;
            Path targetPath = backgroundDir.resolve(fileName).normalize();

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String url = appBaseUrl + "/serveiq/api/app-config/images/backgrounds/" +
                    appType.name().toLowerCase() + "/" + fileName;

            config.setBackgroundImage(url);
            config.setBackgroundImageUrl(url);

            return configRepository.save(config);

        } catch (IOException e) {
            log.error("Failed to save background image: {}", e.getMessage());
            throw new BusinessException("Failed to save background image: " + e.getMessage());
        }
    }

    public AppConfig uploadAppLogo(AppType appType, MultipartFile file) {
        validateImage(file);

        try {
            AppConfig config = getConfig(appType);
            config.setDefaultEnabled(false);

            if (config.getAppLogo() != null &&
                    !config.getAppLogo().startsWith("http") &&
                    !config.getAppLogo().isEmpty()) {
                deleteOldImage(config.getAppLogo());
            }

            Path logoDir = Paths.get(uploadDir, "logos", appType.name().toLowerCase())
                    .toAbsolutePath().normalize();
            Files.createDirectories(logoDir);

            String extension = getExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;
            Path targetPath = logoDir.resolve(fileName).normalize();

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String url = appBaseUrl + "/serveiq/api/app-config/images/logos/" +
                    appType.name().toLowerCase() + "/" + fileName;

            config.setAppLogo(url);
            config.setAppLogoUrl(url);

            return configRepository.save(config);

        } catch (IOException e) {
            log.error("Failed to save app logo: {}", e.getMessage());
            throw new BusinessException("Failed to save app logo: " + e.getMessage());
        }
    }

    public AppConfig resetToDefault(AppType appType) {
        AppConfig config = getConfig(appType);

        config.setDefaultEnabled(true);
        config.setAppLanguage("ENGLISH");
        config.setPrimaryColor("#3E321A");
        config.setSecondaryColor("#9E9B46");

        String logoUrl = getDefaultLogoUrl();
        config.setAppLogoUrl(logoUrl);
        config.setAppLogo(logoUrl);

        switch (appType) {
            case TV_DISPLAY -> {
                config.setTickerEnabled(true);
                config.setTickerSpeed(30);
                config.setVideosEnabled(true);
                config.setFlickerTime(5);
                config.setComponentOrder(new ArrayList<>(List.of("ROOMS", "COUNTERS", "VIDEOS", "TICKER")));
                setDefaultBackgroundImage(config, appType);
            }
            case FEEDBACK -> {
                config.setImageCarouselEnabled(true);
                config.setFeedbackVideoEnabled(false);
                config.setFlickerTime(5);
                setDefaultBackgroundImage(config, appType);
            }
            case KIOSK -> {
                setDefaultBackgroundImage(config, appType);
            }
        }

        return configRepository.save(config);
    }

    public AppConfig updateComponentOrder(AppType appType, ComponentOrderDTO orderDTO) {
        if (appType != AppType.TV_DISPLAY) {
            throw new BusinessException("Component order is only available for TV_DISPLAY");
        }

        AppConfig config = getConfig(appType);

        List<String> validComponents = List.of("ROOMS", "COUNTERS", "VIDEOS", "TICKER");
        List<String> newOrder = orderDTO.getComponentOrder();

        if (newOrder == null || newOrder.size() != 4) {
            throw new BusinessException("Component order must contain exactly 4 components");
        }

        if (!newOrder.containsAll(validComponents) || !validComponents.containsAll(newOrder)) {
            throw new BusinessException("Invalid component order. Must contain: ROOMS, COUNTERS, VIDEOS, TICKER");
        }

        config.setComponentOrder(new ArrayList<>(newOrder));
        config.setDefaultEnabled(false);
        return configRepository.save(config);
    }

    public AppDashboardDTO getAppDashboard(AppType appType) {
        AppConfig config = getConfig(appType);

        if (appType == AppType.TV_DISPLAY) {
            List<BreakingNews> tickers = Boolean.TRUE.equals(config.getTickerEnabled())
                    ? newsRepository.findByPublishedTrueAndActiveTrueAndArchivedFalseOrderByPublishedDateDesc(
                    org.springframework.data.domain.PageRequest.of(0, 20))
                    : List.of();

            // Get VIDEO and IPTV_URL type content for TV Display
            List<TvContent> activeVideos = Boolean.TRUE.equals(config.getVideosEnabled())
                    ? tvContentRepository.findByTypeInAndActiveTrueAndArchivedFalseOrderByCreatedAtDesc(
                    List.of("VIDEO", "IPTV_URL"))
                    : List.of();

            // Convert URLs to full URLs
            activeVideos.forEach(video -> {
                video.setUrl(toFullUrl(video.getUrl()));
                video.setHlsUrl(toFullUrl(video.getHlsUrl()));
            });

            return AppDashboardDTO.builder()
                    .backgroundImage(config.getBackgroundImage())
                    .appLogo(config.getAppLogo())
                    .backgroundImageUrl(config.getBackgroundImageUrl())
                    .appLogoUrl(config.getAppLogoUrl())
                    .appLanguage(config.getAppLanguage())
                    .defaultEnabled(config.getDefaultEnabled())
                    .primaryColor(config.getPrimaryColor())
                    .secondaryColor(config.getSecondaryColor())
                    .tickerEnabled(config.getTickerEnabled())
                    .tickerSpeed(config.getTickerSpeed())
                    .videosEnabled(config.getVideosEnabled())
                    .flickerTime(config.getFlickerTime())
                    .componentOrder(config.getComponentOrder())
                    .tickers(tickers)
                    .activeVideos(activeVideos)
                    .imageCarouselEnabled(null)
                    .feedbackVideoEnabled(null)
                    .activeImages(null)
                    .activeFeedbackVideo(null)
                    .build();
        }

        if (appType == AppType.FEEDBACK) {
            List<TvContent> activeImages = Boolean.TRUE.equals(config.getImageCarouselEnabled())
                    ? tvContentRepository.findByTypeAndActiveTrueAndArchivedFalseOrderByCreatedAtDesc("IMAGE")
                    : List.of();

            List<TvContent> activeFeedbackVideo = Boolean.TRUE.equals(config.getFeedbackVideoEnabled())
                    ? tvContentRepository.findByTypeInAndActiveTrueAndArchivedFalseOrderByCreatedAtDesc(
                    List.of("VIDEO", "IPTV_URL"))
                    : List.of();

            // Convert URLs to full URLs
            activeImages.forEach(image -> {
                image.setUrl(toFullUrl(image.getUrl()));
            });
            activeFeedbackVideo.forEach(video -> {
                video.setUrl(toFullUrl(video.getUrl()));
                video.setHlsUrl(toFullUrl(video.getHlsUrl()));
            });

            return AppDashboardDTO.builder()
                    .backgroundImage(config.getBackgroundImage())
                    .appLogo(config.getAppLogo())
                    .backgroundImageUrl(config.getBackgroundImageUrl())
                    .appLogoUrl(config.getAppLogoUrl())
                    .appLanguage(config.getAppLanguage())
                    .defaultEnabled(config.getDefaultEnabled())
                    .primaryColor(config.getPrimaryColor())
                    .secondaryColor(config.getSecondaryColor())
                    .tickerEnabled(null)
                    .tickerSpeed(null)
                    .videosEnabled(null)
                    .flickerTime(config.getFlickerTime())
                    .componentOrder(null)
                    .tickers(null)
                    .activeVideos(null)
                    .imageCarouselEnabled(config.getImageCarouselEnabled())
                    .feedbackVideoEnabled(config.getFeedbackVideoEnabled())
                    .activeImages(activeImages)
                    .activeFeedbackVideo(activeFeedbackVideo)
                    .build();
        }

        return AppDashboardDTO.builder()
                .backgroundImage(config.getBackgroundImage())
                .appLogo(config.getAppLogo())
                .backgroundImageUrl(config.getBackgroundImageUrl())
                .appLogoUrl(config.getAppLogoUrl())
                .appLanguage(config.getAppLanguage())
                .defaultEnabled(config.getDefaultEnabled())
                .primaryColor(config.getPrimaryColor())
                .secondaryColor(config.getSecondaryColor())
                .tickerEnabled(null)
                .tickerSpeed(null)
                .videosEnabled(null)
                .flickerTime(null)
                .componentOrder(null)
                .tickers(null)
                .activeVideos(null)
                .imageCarouselEnabled(null)
                .feedbackVideoEnabled(null)
                .activeImages(null)
                .activeFeedbackVideo(null)
                .build();
    }

    /**
     * Convert relative URL to full URL
     */
    private String toFullUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        // If it's a relative path starting with /serveiq or /images or /videos
        if (url.startsWith("/serveiq/") || url.startsWith("/images/") || url.startsWith("/videos/")) {
            return appBaseUrl + url;
        }
        // Otherwise, prepend base URL
        return appBaseUrl + "/" + url;
    }

    private void deleteOldImage(String imagePath) {
        try {
            if (imagePath != null && !imagePath.isEmpty() &&
                    !imagePath.startsWith("http") &&
                    !imagePath.startsWith(defaultImageDir)) {
                Path path = Paths.get(imagePath);
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.info("Deleted old image: {}", imagePath);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to delete old image: {}", e.getMessage());
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("Image is larger than 10MB limit");
        }
        if (file.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new BusinessException("Only JPG, PNG, WebP, and GIF images are allowed");
        }
    }

    private String getExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        }
        return ".jpg";
    }
}