package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponseDTO {

    /** Bearer token. Send as "Authorization: Bearer <token>" on every request. */
    private String token;
    private Long expiresInSeconds;
    private UserResponseDTO user;

    public String getTokenType() {
        return "Bearer";
    }
}
