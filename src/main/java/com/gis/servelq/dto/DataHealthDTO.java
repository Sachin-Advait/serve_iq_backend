package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataHealthDTO {
    private String systemCode;
    private boolean databaseReachable;
    private long currentRecordCount;
    private Instant latestGeneratedAt;
    private boolean hasData;
    private String message;
}