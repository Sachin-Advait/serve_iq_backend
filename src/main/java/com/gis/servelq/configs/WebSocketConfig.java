package com.gis.servelq.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * server→client and expected client→server heartbeat, in ms
     */
    private static final long HEARTBEAT_MS = 10_000;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(brokerHeartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Dedicated scheduler for STOMP heartbeats. Deliberately NOT a @Bean:
     * a TaskScheduler bean would replace the one @EnableScheduling uses
     * for your @Scheduled jobs (like WebSocketHeartbeatConfig).
     */
    private ThreadPoolTaskScheduler brokerHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Was setAllowedOriginPatterns("*"), so a page on any origin could open
        // a socket and subscribe to every branch's queue traffic.
        registry.addEndpoint("/serveiq/ws").setAllowedOrigins("*");
    }
}