package com.gis.servelq.dto;

import lombok.Data;

@Data
public class CounterStatusResponseDTO {
    private String counterId;
    private String code;
    private String name;

    private Boolean enabled;
    private Boolean paused;
    private String status;

    // These fields will be null when no token is assigned or upcoming
    private String tokenNumber;
    private String tokenId;
    private String serviceId;
    private String serviceName;

    public void clearTokenDetails() {
        this.tokenId = null;
        this.tokenNumber = null;
        this.serviceId = null;
        this.serviceName = null;
    }
}
