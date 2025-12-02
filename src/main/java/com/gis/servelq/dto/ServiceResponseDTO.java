package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.ServiceModel;
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

    private List<String> childrenIds = List.of(); // ← Always non-null

    private Integer slaSec;
    private Boolean enabled;
    private String branchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ServiceResponseDTO fromEntity(ServiceModel service) {
        ServiceResponseDTO dto = new ServiceResponseDTO();
        dto.setId(service.getId());
        dto.setCode(service.getCode());
        dto.setName(service.getName());
        dto.setArabicName(service.getArabicName());
        dto.setParentId(service.getParentId());

        // Only override if children exist
        if (service.getChildren() != null && !service.getChildren().isEmpty()) {
            dto.setChildrenIds(
                    service.getChildren()
                            .stream()
                            .map(ServiceModel::getId)
                            .toList()
            );
        }

        dto.setSlaSec(service.getSlaSec());
        dto.setEnabled(service.getEnabled());
        dto.setBranchId(service.getBranchId());
        dto.setCreatedAt(service.getCreatedAt());
        dto.setUpdatedAt(service.getUpdatedAt());
        return dto;
    }
}
