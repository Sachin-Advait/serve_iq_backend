package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.ServiceModel;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceResponseDTO {
    private String id;
    private String code;
    private String name;
    private String parentId;
    private Integer slaSec;
    private Boolean enabled;
    private String branchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Static method to convert from Entity to DTO
    public static ServiceResponseDTO fromEntity(ServiceModel service) {
        ServiceResponseDTO dto = new ServiceResponseDTO();
        dto.setId(service.getId());
        dto.setCode(service.getCode());
        dto.setName(service.getName());
        dto.setParentId(service.getParentId());
        dto.setSlaSec(service.getSlaSec());
        dto.setEnabled(service.getEnabled());
        dto.setBranchId(service.getBranchId());
        dto.setCreatedAt(service.getCreatedAt());
        dto.setUpdatedAt(service.getUpdatedAt());
        return dto;
    }
}