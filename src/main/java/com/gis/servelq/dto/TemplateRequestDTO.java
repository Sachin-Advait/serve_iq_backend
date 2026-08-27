package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRequestDTO {
    private String templateName;
    private String description;

    // Common fields
    private String backgroundImage;
    private String appLogo;
    private String backgroundImageUrl;
    private String appLogoUrl;
    private String appLanguage;
    private Boolean defaultEnabled;
    private String primaryColor;
    private String secondaryColor;

    // TV Display fields
    private Boolean tickerEnabled;
    private Integer tickerSpeed;
    private Boolean videosEnabled;
    private Integer flickerTime;
    private List<String> componentOrder;

    // Feedback fields
    private Boolean imageCarouselEnabled;
    private Boolean feedbackVideoEnabled;
}