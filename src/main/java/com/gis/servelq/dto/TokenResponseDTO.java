package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponseDTO {

    private String id;
    private String token;

    private String serviceName;
    private String serviceCode;
    private String serviceId;

    private TokenStatus status;

    private String counterId;
    private String counterName;

    // Reporting Fields
    private String generatedTime;
    private String startTime;
    private String endTime;
    private String waitTime;
    private String servingDuration;

    public static TokenResponseDTO fromEntity(Token token) {
        TokenResponseDTO dto = new TokenResponseDTO();

        dto.setId(token.getId());
        dto.setToken(token.getToken());
        dto.setServiceId(token.getServiceId());
        dto.setServiceName(token.getServiceName());
        dto.setStatus(token.getStatus());
        dto.setCounterId(token.getAssignedCounterId());
        dto.setCounterName(token.getAssignedCounterName());

        // Optional date/time fields → format only if not null
        if (token.getCreatedAt() != null)
            dto.setGeneratedTime(token.getCreatedAt().toString());

        if (token.getStartAt() != null)
            dto.setStartTime(token.getStartAt().toString());

        if (token.getEndAt() != null)
            dto.setEndTime(token.getEndAt().toString());

        // waitTime = startTime - generatedTime
        if (token.getStartAt() != null && token.getCreatedAt() != null)
            dto.setWaitTime(calculateDuration(token.getCreatedAt(), token.getStartAt()));

        // servingDuration = endTime - startTime
        if (token.getStartAt() != null && token.getEndAt() != null)
            dto.setServingDuration(calculateDuration(token.getStartAt(), token.getEndAt()));

        return dto;
    }

    private static String calculateDuration(LocalDateTime start, LocalDateTime end) {
        long min = java.time.Duration.between(start, end).toMinutes();
        return min + " min";
    }
}
