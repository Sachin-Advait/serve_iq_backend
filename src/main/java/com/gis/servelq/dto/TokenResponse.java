package com.gis.servelq.dto;
import com.gis.servelq.models.TokenStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TokenResponse {
    private String id;
    private String token;
    private String serviceName;
    private String serviceCode;
    private TokenStatus status;
    private Integer waitingCount;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedTime;
}
