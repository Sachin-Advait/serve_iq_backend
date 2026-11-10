package com.gis.servelq.dto;

import lombok.Data;

import java.util.List;

/**
 * What the System page reads and saves. mailConfigured says whether MAIL_HOST
 * is set in the backend .env (no mail can go out without it).
 */
@Data
public class TokenAlertSettingsDTO {
    private Boolean enabled;
    private Integer waitingThreshold;
    private List<String> roles;
    private Boolean mailConfigured;
}
