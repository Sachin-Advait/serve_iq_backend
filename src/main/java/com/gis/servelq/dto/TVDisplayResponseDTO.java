package com.gis.servelq.dto;

import lombok.Data;

import java.util.List;

@Data
public class TVDisplayResponseDTO {
    private List<DisplayToken> latestCalls;
    private List<DisplayToken> nowServing;
    private List<String> upcomingTokens;
    private List<String> holdTokens;
    // Every counter in the branch, for the TV's "Current counters" grid
    private List<CounterTile> counters;
    private String branchName;

    private long waiting;
    private long serving;
    private long noShow;
    private long servedToday;

    @Data
    public static class DisplayToken {
        private String token;
        private String counter;
        private String service;
    }

    @Data
    public static class CounterTile {
        private String counter;
        private String code;
        // CALLING, SERVING, IDLE or CLOSED (disabled, paused or no agent)
        private String status;
        private String token;
    }
}
