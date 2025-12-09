package com.gis.servelq.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppService {

    @Value("${twilio.sid}")
    private String accountSid;

    @Value("${twilio.token}")
    private String authToken;

    @Value("${twilio.from}")
    private String from;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    public String sendMessage(String to, String message) {

        Message msg = Message.creator(
                new PhoneNumber("whatsapp:" + to),
                new PhoneNumber(from),
                message
        ).create();

        return msg.getSid();
    }
}
