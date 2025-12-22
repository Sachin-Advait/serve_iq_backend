package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import lombok.Data;

@Data
public class CounterUpdateRequest {
    private String code;
    private String name;
    private String branchId;
    private String userId;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status = CounterStatus.IDLE;
    private String serviceId;
}

