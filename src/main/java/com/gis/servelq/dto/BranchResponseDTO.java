package com.gis.servelq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.gis.servelq.models.Branch;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BranchResponseDTO {
    private String id;
    private String code;
    private String name;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Boolean ticketIssuingEnabled;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime ticketIssuingStart;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime ticketIssuingEnd;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime officeCloseTime;
    private String ticketStopMessageEn;
    private String ticketStopMessageAr;

    public static BranchResponseDTO fromEntity(Branch branch) {
        BranchResponseDTO dto = new BranchResponseDTO();
        dto.setId(branch.getId());
        dto.setCode(branch.getCode());
        dto.setName(branch.getName());
        dto.setEnabled(branch.getEnabled());
        return dto;
    }
}
