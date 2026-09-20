package com.gis.servelq.dto;

import lombok.Data;

@Data
public class CounterOptionDTO {
    private String id;
    private String code;
    private String name;
    private String serviceName;
    private boolean occupied;
    private String occupiedByName;
    private boolean mine;
}