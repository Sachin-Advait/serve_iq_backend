package com.gis.servelq.dto;

import com.gis.servelq.models.Token;
import lombok.Data;

@Data
public class TokenDTO {
    private String id;
    private String token;
    private String serviceId;
    private String serviceName;
    private Integer priority;
    private String status;

    // Static method inside the class to convert entity -> DTO
    public static TokenDTO fromEntity(Token token) {
        TokenDTO dto = new TokenDTO();
        dto.setId(token.getId());
        dto.setToken(token.getToken());
        dto.setServiceId(token.getServiceId());
        // Accessing service name safely (lazy-loaded)
        if (token.getService() != null) {
            dto.setServiceName(token.getService().getName());
        }
        dto.setPriority(token.getPriority());
        dto.setStatus(token.getStatus().name());
        return dto;
    }
}
