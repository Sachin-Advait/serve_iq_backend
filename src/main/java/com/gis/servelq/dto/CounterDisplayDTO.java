package com.gis.servelq.dto;

import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.time.LocalDateTime;

// Nulls must be sent, not dropped: when a token is held, transferred or marked
// no-show the counter has no token any more, and a missing tokenNumber /
// calledAt left the meeting TV still showing the old token.
@Data
public class CounterDisplayDTO {

    private String counterName;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus counterStatus;

    private String tokenNumber;

    private String serviceName;
    private String arabicService;

    private LocalDateTime calledAt;
}