package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.util.List;

@Data
public class CounterUpdateRequest {
    private String code;
    private String name;
    private String branchId;
    private String userId;
    private Boolean enabled;
    private Boolean paused;
    // No default: an admin edit that doesn't send a status must keep the
    // counter's live one (defaulting to IDLE turned a closed counter idle).
    private CounterStatus status;
    private String serviceId;
    private List<String> serviceIds;
}

