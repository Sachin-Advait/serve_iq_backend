package com.gis.servelq.dto;

import lombok.Data;

/** Sends one template with sample values to a phone, to check the text. */
@Data
public class SmsTestRequest {
    private String code;
    private String to;
    private String language;
}
