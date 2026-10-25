package com.gis.servelq.dto;

import com.gis.servelq.models.SmsTemplate;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SmsTemplateDTO {
    private String id;
    private String code;
    private String name;
    private String description;
    private String bodyEn;
    private String bodyAr;
    private Boolean active;
    private List<String> placeholders;
    private LocalDateTime updatedAt;

    public static SmsTemplateDTO from(SmsTemplate t, List<String> placeholders) {
        SmsTemplateDTO dto = new SmsTemplateDTO();
        dto.setId(t.getId());
        dto.setCode(t.getCode());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setBodyEn(t.getBodyEn());
        dto.setBodyAr(t.getBodyAr());
        dto.setActive(t.getActive());
        dto.setPlaceholders(placeholders);
        dto.setUpdatedAt(t.getUpdatedAt());
        return dto;
    }
}
