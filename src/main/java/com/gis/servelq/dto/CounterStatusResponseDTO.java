package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import lombok.Data;

@Data
public class CounterStatusResponseDTO {
    private String counterId;
    private String code;
    private String name;

    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status;

    // These fields will be null when no token is assigned or upcoming
    private String tokenNumber;
    private String tokenId;
    private String serviceId;
    private String serviceName;
    private String serviceCode;  // ADD THIS FIELD

    public void clearTokenDetails() {
        this.tokenId = null;
        this.tokenNumber = null;
        this.serviceId = null;
        this.serviceName = null;
        this.serviceCode = null;  // ADD THIS LINE
    }
}