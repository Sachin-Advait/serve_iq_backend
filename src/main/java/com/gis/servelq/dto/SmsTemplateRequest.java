package com.gis.servelq.dto;

import lombok.Data;

/** Fields left null are not changed. */
@Data
public class SmsTemplateRequest {
    private String bodyEn;
    private String bodyAr;
    private Boolean active;
}
