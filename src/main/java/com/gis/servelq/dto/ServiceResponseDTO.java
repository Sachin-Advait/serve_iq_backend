package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.Services;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceResponseDTO {
    private String id;
    private String code;
    private String name;
    private String arabicName;
    private String parentId;
    private List<String> childrenIds = List.of();
    private List<String> counterIds = List.of();
    private Boolean enabled;
    private String branchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ServiceResponseDTO fromEntity(Services service) {
        ServiceResponseDTO dto = new ServiceResponseDTO();
        dto.setId(service.getId());
        dto.setCode(service.getCode());
        dto.setName(service.getName());
        dto.setArabicName(service.getArabicName());
        dto.setParentId(service.getParentId());

        if (service.getChildren() != null && !service.getChildren().isEmpty()) {
            dto.setChildrenIds(service.getChildren());
        }
        if (service.getCounterIds() != null && !service.getCounterIds().isEmpty()) {
            dto.setCounterIds(service.getCounterIds());
        }
        dto.setEnabled(service.getEnabled());
        dto.setBranchId(service.getBranchId());
        dto.setCreatedAt(service.getCreatedAt());
        dto.setUpdatedAt(service.getUpdatedAt());
        return dto;
    }
}
