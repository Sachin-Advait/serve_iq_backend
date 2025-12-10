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
    private String mobileNumber;

    private TokenStatus status;

    private String counterId;
    private String counterName;

    private String transferCounterName;
    private Boolean isTransfer;

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
        dto.setMobileNumber(token.getMobileNumber());
        dto.setStatus(token.getStatus());

        dto.setTransferCounterName(token.getTransferFrom());
        dto.setIsTransfer(token.getIsTransfer());

        dto.setCounterId(token.getAssignedCounterId());
        dto.setCounterName(token.getAssignedCounterName());

        dto.setGeneratedTime(token.getCreatedAt().toString());
        dto.setStartTime(token.getStartAt().toString());
        dto.setEndTime(token.getEndAt().toString());
        dto.setWaitTime(calculateDuration(token.getCreatedAt(), token.getStartAt()));
        dto.setServingDuration(calculateDuration(token.getStartAt(), token.getEndAt()));

        return dto;
    }

    private static String calculateDuration(LocalDateTime start, LocalDateTime end) {
        long min = java.time.Duration.between(start, end).toMinutes();
        return min + " min";
    }
}
