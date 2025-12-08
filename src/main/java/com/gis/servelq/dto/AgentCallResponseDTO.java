package com.gis.servelq.dto;


import lombok.Data;

@Data
public class AgentCallResponseDTO {
    private String tokenId;
    private String token;
    private String serviceName;
    private String mobileNumber;
    private String counterId;
    private String counterName;
    private Integer waitingCount;
}