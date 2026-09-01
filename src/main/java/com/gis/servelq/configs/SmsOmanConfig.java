package com.gis.servelq.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smsoman")
public record SmsOmanConfig(
        String url,
        String username,
        String password,
        String sender,
        String priority,
        String appId
) {
    public SmsOmanConfig {
        if (url == null) url = "https://smsoman.com/API/sendsms";
        if (priority == null) priority = "1";
        if (appId == null) appId = "serveiq";
    }
}