package com.gis.servelq.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(TokenEvent event) {
        publisher.publishEvent(event);
    }
}
