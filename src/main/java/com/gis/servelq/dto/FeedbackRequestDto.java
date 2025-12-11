package com.gis.servelq.dto;

import com.gis.servelq.models.Feedback.MoodRating;
import lombok.Data;

@Data
public class FeedbackRequestDto {

    private String tokenId;
    private String counterCode;
    private String agentName;

    private MoodRating rating;
    private String review;
}
