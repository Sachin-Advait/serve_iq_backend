package com.gis.servelq.dto;

import lombok.Data;

import java.util.List;

@Data
public class TVDisplayResponseDTO {
    private List<DisplayToken> latestCalls;
    private List<DisplayToken> nowServing;
    private List<String> upcomingTokens;
    private String branchName;

    @Data
    public static class DisplayToken {
        private String token;
        private String counter;
        private String service;
    }
}
