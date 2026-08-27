package com.gis.servelq.models;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_configs")
@Data
public class AppConfig {
    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String appType = "TV_DISPLAY";

    // ==================== COMMON FIELDS ====================
    private String backgroundImage;
    private String appLogo;
    private String backgroundImageUrl;
    private String appLogoUrl;
    private String appLanguage = "ENGLISH";
    private Boolean defaultEnabled;
    private String primaryColor = "#3e321a";
    private String secondaryColor = "#9E9B46";

    // ==================== TV DISPLAY FIELDS ====================
    private Boolean tickerEnabled;
    private Integer tickerSpeed;
    private Boolean videosEnabled;      // TV: Enable video playback
    private Integer flickerTime;        // TV: Rotation time in seconds

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_config_component_order", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "component")
    @OrderColumn(name = "position")
    private List<String> componentOrder = new ArrayList<>(List.of(
            "ROOMS",
            "COUNTERS",
            "VIDEOS",
            "TICKER"
    ));

    // ==================== FEEDBACK FIELDS ====================
    // Feedback media mode (mutually exclusive)
    private Boolean imageCarouselEnabled;  // Show rotating images on feedback screen
    private Boolean feedbackVideoEnabled;  // Show video on feedback screen
}