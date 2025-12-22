package com.gis.servelq.dto;

import lombok.Data;

@Data
public class BranchUpdateRequest {
    private String code;
    private String name;
    private Boolean enabled;
}
