package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.CounterStatus;
import com.gis.servelq.models.Services;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CounterResponseDTO {

    private String id;
    private String code;
    private String name;
    private BranchResponseDTO branch;
    private Boolean enabled;
    private Boolean paused;
    private CounterStatus status;
    private String userId;
    private String username;
    private ServiceResponseDTO service;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ----------- STATIC FACTORY METHOD -------------
    public static CounterResponseDTO fromEntity(
            Counter counter,
            Branch branch,
            String username,
            Services service
    ) {
        CounterResponseDTO dto = new CounterResponseDTO();

        dto.setId(counter.getId());
        dto.setCode(counter.getCode());
        dto.setName(counter.getName());
        dto.setEnabled(counter.getEnabled());
        dto.setPaused(counter.getPaused());
        dto.setStatus(counter.getStatus());
        dto.setUserId(counter.getUserId());
        dto.setUsername(username);
        dto.setCreatedAt(counter.getCreatedAt());
        dto.setUpdatedAt(counter.getUpdatedAt());

        // branch
        dto.setBranch(BranchResponseDTO.fromEntity(branch));

        // service (if exists)
        if (service != null) {
            dto.setService(ServiceResponseDTO.fromEntity(service));
        }

        return dto;
    }
}
