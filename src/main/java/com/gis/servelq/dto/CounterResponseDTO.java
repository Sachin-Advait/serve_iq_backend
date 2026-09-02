package com.gis.servelq.dto;

import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CounterResponseDTO {
    private String id;
    private String code;
    private String name;
    private String branchId;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status;
    private String userId;
    private String username;
    private String serviceId;
    private String serviceName;  // NEW
    private String serviceCode;  // NEW
    private Double avgSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CounterResponseDTO fromEntity(Counter counter, String username, Double avgSeconds) {
        CounterResponseDTO dto = new CounterResponseDTO();
        dto.setId(counter.getId());
        dto.setCode(counter.getCode());
        dto.setName(counter.getName());
        dto.setBranchId(counter.getBranchId());
        dto.setEnabled(counter.getEnabled());
        dto.setPaused(counter.getPaused());
        dto.setStatus(counter.getStatus());
        dto.setUserId(counter.getUserId());
        dto.setUsername(username);
        dto.setServiceId(counter.getServiceId());
        dto.setAvgSeconds(avgSeconds);
        dto.setCreatedAt(counter.getCreatedAt());
        dto.setUpdatedAt(counter.getUpdatedAt());
        return dto;
    }
}