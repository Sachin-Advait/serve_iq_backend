package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponseDTO {

    private static final ObjectMapper QR_MAPPER = new ObjectMapper();

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

    private Long unassignedTokenCount;
    private String qrValue;

    // Reporting Fields
    private String generatedTime;
    private String startTime;
    private String endTime;
    private String waitTime;
    private String servingDuration;

    public static TokenResponseDTO fromEntity(Token token) {
        return fromEntity(token, 0L);
    }
    
    public static TokenResponseDTO fromEntity(Token token, long unassignedTokenCount) {
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

        dto.setUnassignedTokenCount(unassignedTokenCount);
        dto.setQrValue(buildQrValue(token));

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

    /**
     * Builds the JSON string encoded into the receipt's QR code, so a scan
     * carries enough to identify and verify the token without another lookup.
     * Falls back to the bare token number if serialization ever fails - a
     * mapper error here should never block token generation.
     */
    public static String buildQrValue(Token token) {
        try {
            TokenQrPayload payload = new TokenQrPayload(
                    token.getId(),
                    token.getToken(),
                    token.getServiceId(),
                    token.getServiceName(),
                    token.getBranchId(),
                    token.getCreatedAt() != null ? token.getCreatedAt().toString() : null
            );
            return QR_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return token.getToken();
        }
    }

    private static String calculateDuration(LocalDateTime start, LocalDateTime end) {
        long min = java.time.Duration.between(start, end).toMinutes();
        return min + " min";
    }

    private record TokenQrPayload(
            String id,
            String token,
            String serviceId,
            String serviceName,
            String branchId,
            String generatedTime
    ) {
    }
}
