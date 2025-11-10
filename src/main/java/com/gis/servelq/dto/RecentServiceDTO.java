package com.gis.servelq.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecentServiceDTO {
    private String token;
    private String civilId;
    private String serviceName;
    private Long timeTakenInSeconds;

    public static RecentServiceDTO fromEntity(String token, String civilId, String serviceName, LocalDateTime startAt, LocalDateTime endAt) {
        RecentServiceDTO dto = new RecentServiceDTO();
        dto.setToken(token);
        dto.setCivilId(civilId);
        dto.setServiceName(serviceName);
        if (startAt != null && endAt != null) {
            dto.setTimeTakenInSeconds(java.time.Duration.between(startAt, endAt).getSeconds());
        } else {
            dto.setTimeTakenInSeconds(null);
        }
        return dto;
    }
}
