package com.gis.servelq.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class WhatsAppService {

    @Value("${twilio.sid:}")
    private String accountSid;

    @Value("${twilio.token:}")
    private String authToken;

    @Value("${twilio.from:}")
    private String from;

    private boolean enabled;

    @PostConstruct
    public void init() {
        // Credentials are optional now that they come from the environment.
        // Without this guard Twilio.init throws on startup for anyone running
        // locally without a Twilio account.
        enabled = accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && from != null && !from.isBlank();

        if (enabled) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialised - WhatsApp notifications are enabled");
        } else {
            log.warn("Twilio credentials are not configured - WhatsApp notifications are disabled");
        }
    }

    /**
     * Fire and forget send used by token issuance.
     *
     * Kept separate from sendMessage on purpose: the WhatsApp controller calls
     * that one and returns the SID to its caller, so making the shared method
     * @Async would silently start returning null there.
     */
    @Async
    public void sendTokenNotificationAsync(String mobileNumber, String tokenNumber) {
        String message = String.format(
                "Your token number %s has been generated successfully.%nPlease monitor the TV display for your turn.",
                tokenNumber);
        sendMessage("+968" + mobileNumber, message);
    }

    public String sendMessage(String to, String messageText) {
        if (!enabled) {
            log.debug("WhatsApp is disabled - not sending to {}", to);
            return null;
        }
        try {
            Message msg = Message.creator(
                    new PhoneNumber("whatsapp:" + to),
                    new PhoneNumber(from),
                    messageText
            ).create();

            log.info("WhatsApp message sent, SID: {}", msg.getSid());
            return msg.getSid();

        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", to, e.getMessage(), e);
            return null;
        }
    }
}
