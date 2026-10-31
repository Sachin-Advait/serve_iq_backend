package com.gis.servelq.configs;

import com.gis.servelq.services.SocketVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketHeartbeatConfig {

    private final SimpMessagingTemplate messagingTemplate;
    private final SocketVersionService socketVersionService;

    /**
     * Send a PING to /topic/heartbeat every 10 seconds. Every app socket
     * subscribes to it and reconnects after 25s without one, so a socket that
     * died silently is replaced in seconds instead of missing pushes.
     * The body lists every topic's latest push version (old apps ignore it).
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        try {
            // Send to all branches - or make this dynamic based on active subscriptions
            messagingTemplate.convertAndSend("/topic/heartbeat",
                    Map.of("type", "PING", "versions", socketVersionService.snapshot()));
            log.debug("Heartbeat sent to Globally");
        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }
}
