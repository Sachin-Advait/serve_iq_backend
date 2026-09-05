package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CounterDisplayDTO {

    private String counterId;
    private String counterCode;
    private String counterName;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus counterStatus;

    private String tokenId;
    private String tokenNumber;

    private String serviceId;
    private String serviceName;

    private LocalDateTime calledAt;
}