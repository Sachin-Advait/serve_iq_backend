package com.gis.servelq.dto;

import com.gis.servelq.models.BreakingNews;
import com.gis.servelq.models.TvContent;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AppDashboardDTO {
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
    private List<BreakingNews> tickers;
    private List<TvContent> activeVideos;

    // Feedback fields
    private Boolean imageCarouselEnabled;
    private Boolean feedbackVideoEnabled;
    private List<TvContent> activeImages;
    private List<TvContent> activeFeedbackVideo;

    // ==================== KIOSK PRINT FIELDS ====================

    private Boolean kioskPrintEnabled;

    private String kioskHeaderTitleEn;
    private String kioskHeaderTitleAr;
    private String kioskHeaderSubtitleEn;
    private String kioskHeaderSubtitleAr;

    private String kioskTokenTitleEn;
    private String kioskTokenTitleAr;

    private String kioskServiceLabelEn;
    private String kioskServiceLabelAr;

    private String kioskDateLabelEn;
    private String kioskDateLabelAr;
    private String kioskTimeLabelEn;
    private String kioskTimeLabelAr;

    private String kioskWaitingMessageEn;
    private String kioskWaitingMessageAr;

    private String kioskThankYouMessageEn;
    private String kioskThankYouMessageAr;
}