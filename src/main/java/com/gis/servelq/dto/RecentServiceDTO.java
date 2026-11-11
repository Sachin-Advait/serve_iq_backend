package com.gis.servelq.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecentServiceDTO {
    private String token;
    private String arabicToken;
    private String civilId;
    private String serviceName;
    private String serviceArabicName;
    private Long timeTakenInMinutes;
    // Full duration, so the agent app can show seconds for short visits
    private Long timeTakenInSeconds;
    private String note;
    // Visitor feedback for this ticket: rating SAD / NEUTRAL / HAPPY and the
    // optional comment; both null when no feedback was given.
    private String feedbackRating;
    private String feedbackReview;

    public static RecentServiceDTO fromEntity(String token, String civilId, String serviceName,
                                              LocalDateTime startAt, LocalDateTime endAt, String note) {
        RecentServiceDTO dto = new RecentServiceDTO();
        dto.setToken(token);
        dto.setCivilId(civilId);
        dto.setServiceName(serviceName);
        dto.setNote(note);

        if (startAt != null && endAt != null) {
            java.time.Duration taken = java.time.Duration.between(startAt, endAt);
            dto.setTimeTakenInMinutes(taken.toMinutes());
            dto.setTimeTakenInSeconds(taken.getSeconds());
        }

        return dto;
    }
}
