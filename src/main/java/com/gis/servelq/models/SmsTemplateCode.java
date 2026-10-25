package com.gis.servelq.models;

import java.util.List;

/**
 * The messages the backend sends, each with the placeholders it fills in.
 * A new message needs a constant here, a row in sms_templates and a caller.
 */
public enum SmsTemplateCode {
    TOKEN_RECEIPT(List.of("token", "service", "queue", "date", "time"));

    private final List<String> placeholders;

    SmsTemplateCode(List<String> placeholders) {
        this.placeholders = placeholders;
    }

    public List<String> placeholders() {
        return placeholders;
    }
}
