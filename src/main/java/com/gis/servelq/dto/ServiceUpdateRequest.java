package com.gis.servelq.dto;

import lombok.Data;

import java.util.List;

@Data
public class ServiceUpdateRequest {
    private String code;
    private String name;
    private String arabicName;
    private String parentId;
    private List<String> counterIds;
    private Boolean enabled;
    private String branchId;
}
