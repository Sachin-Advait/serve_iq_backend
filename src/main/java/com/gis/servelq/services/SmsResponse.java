package com.gis.servelq.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SmsResponse {

    @JsonProperty("StatusCode")
    private String statusCode;

    @JsonProperty("StatusDesc")
    private String statusDesc;

    @JsonProperty("Proccessed")
    private Integer processed;

    @JsonProperty("BatchRefCode")
    private String batchRefCode;
}