package com.gis.servelq.dto;

import lombok.Data;

@Data
public class TransferReasonRequest {
    private String titleEn;
    private String titleAr;
    private Boolean active;
    private Integer sortOrder;
}
