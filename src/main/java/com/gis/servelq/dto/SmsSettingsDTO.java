package com.gis.servelq.dto;

import lombok.Data;

/**
 * What the admin panel reads and saves. sender/priority/appId are the saved
 * values (blank = use the environment); the default* fields show the
 * environment values, and credentialsConfigured says whether the SMSOMAN
 * username and password are set, without revealing them.
 */
@Data
public class SmsSettingsDTO {
    private Boolean enabled;
    private String sender;
    private Integer priority;
    private String appId;
    /** "en", "ar", or null/blank for the visitor's kiosk language. */
    private String language;

    private String defaultSender;
    private String defaultPriority;
    private String defaultAppId;
    private Boolean credentialsConfigured;
}
