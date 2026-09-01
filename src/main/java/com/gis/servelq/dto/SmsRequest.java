package com.gis.servelq.dto;

import lombok.Data;

@Data
public class SmsRequest {

    private String to;
    private String tokenNo;
    private String serviceEn;
    private String serviceAr;
    private String date;
    private String time;
    private String currentQueue;
    private String qrValue;
    private String language;
}