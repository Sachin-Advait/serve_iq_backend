package com.gis.servelq.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenEvent {
    private TokenEventType type;
    private String branchId;
    private String tokenId;
    private String tokenNo;
    private String counterId;
    private Instant timestamp;
}

