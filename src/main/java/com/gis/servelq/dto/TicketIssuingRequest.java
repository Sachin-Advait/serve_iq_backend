package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalTime;

@Data
public class TicketIssuingRequest {
    private Boolean enabled;
    // "HH:mm"; both null means tickets can be issued at any time of day
    @JsonFormat(pattern = "HH:mm")
    private LocalTime start;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime end;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime officeCloseTime;
    private String messageEn;
    private String messageAr;
}
