package com.gis.servelq.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.List;

@Data
public class ServiceUpdateRequest {
    private String code;
    private String name;
    private String arabicName;
    private String description;
    private String arabicDescription;
    private String parentId;
    private List<String> counterIds;
    private Boolean enabled;
    // 0 clears it, so a sub-service goes back to using its parent's SLA
    @PositiveOrZero
    private Integer slaSeconds;
    // Same rule for the serving-time SLA
    private Integer servingSlaSeconds;
    private String branchId;
}
