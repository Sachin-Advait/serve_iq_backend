package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CounterDisplayDTO {

    private String counterName;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus counterStatus;

    private String tokenNumber;

    private String serviceName;
    private String arabicService;

    private LocalDateTime calledAt;
}