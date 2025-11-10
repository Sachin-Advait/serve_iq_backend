package com.gis.servelq.dto;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentCallResponse {
    private String tokenId;
    private String token;
    private String serviceName;
    private String civilId;
    private String counterId;
    private String counterName;
    private LocalDateTime calledAt;
    private Integer waitingCount;
}