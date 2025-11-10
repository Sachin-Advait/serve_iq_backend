package com.gis.servelq.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TVDisplayResponse {
    private List<DisplayToken> latestCalls;
    private List<DisplayToken> nowServing;
    private List<String> upcomingTokens;
    private String branchName;

    @Data
    public static class DisplayToken {
        private String token;
        private String counter;
        private String service;
        private LocalDateTime calledAt;
    }
}
