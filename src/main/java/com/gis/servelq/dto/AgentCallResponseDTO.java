package com.gis.servelq.dto;


import com.gis.servelq.models.Token;
import lombok.Data;

@Data
public class AgentCallResponseDTO {
    private String tokenId;
    private String token;
    private String serviceName;
    private String serviceArabicName;
    private String mobileNumber;

    public static AgentCallResponseDTO fromEntity(Token token) {
        AgentCallResponseDTO response = new AgentCallResponseDTO();
        response.setTokenId(token.getId());
        response.setToken(token.getToken());
        response.setServiceName(token.getServiceName());
        response.setServiceArabicName(token.getServiceArabicName());
        response.setMobileNumber(token.getMobileNumber());
        return response;
    }
}