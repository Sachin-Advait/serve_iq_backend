package com.gis.servelq.dto;

import lombok.Data;

@Data
public class TokenTransferRequest {
    private String tokenId;
    private String toCounterId;
}
