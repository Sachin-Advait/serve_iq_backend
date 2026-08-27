package com.gis.servelq.dto;

import com.gis.servelq.models.BreakingNews;
import com.gis.servelq.models.TvContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppDashboardDTO {
    // ==================== COMMON FIELDS ====================
    private String backgroundImage;
    private String appLogo;
    private String backgroundImageUrl;
    private String appLogoUrl;
    private String appLanguage;
    private Boolean defaultEnabled;
    private String primaryColor;
    private String secondaryColor;

    // ==================== TV DISPLAY FIELDS ====================
    private Boolean tickerEnabled;
    private Integer tickerSpeed;
    private Boolean videosEnabled;
    private Integer flickerTime;
    private List<String> componentOrder;
    private List<BreakingNews> tickers;
    private List<TvContent> activeVideos;

    // ==================== FEEDBACK FIELDS ====================
    private Boolean imageCarouselEnabled;
    private Boolean feedbackVideoEnabled;
    private List<TvContent> activeImages;
    private List<TvContent> activeFeedbackVideo;
}