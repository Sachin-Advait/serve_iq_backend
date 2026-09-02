package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDTO {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long adminUsers;
    private Map<String, Long> roleDistribution;
}