package com.gis.servelq.dto;
import com.gis.servelq.models.Token;
import lombok.Data;

@Data
public class LiveTokenDTO {
    private String counterId;
    private String token;
    private String serviceName;

    public static LiveTokenDTO fromEntity(Token token) {
        LiveTokenDTO dto = new LiveTokenDTO();
        dto.setCounterId(token.getCounterId()); // might be null if not called yet
        dto.setToken(token.getToken());
        dto.setServiceName(token.getService() != null ? token.getService().getName() : null);
        return dto;
    }
}

