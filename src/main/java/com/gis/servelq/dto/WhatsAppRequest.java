package com.gis.servelq.dto;

import lombok.Data;

@Data
public class WhatsAppRequest {
    private String to;
    private String msg;
}