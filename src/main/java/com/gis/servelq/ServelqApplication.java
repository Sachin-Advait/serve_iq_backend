package com.gis.servelq;

import com.gis.servelq.configs.SmsOmanConfig;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EntityScan(basePackages = "com.gis.servelq.models")
@EnableJpaRepositories(basePackages = "com.gis.servelq.repository")
@EnableConfigurationProperties(SmsOmanConfig.class)
public class ServelqApplication implements ApplicationListener<WebServerInitializedEvent> {

    public static void main(String[] args) {
        loadDotenvIntoSystemProperties();
        useBranchTimeZone();
        SpringApplication.run(ServelqApplication.class, args);
    }

    /**
     * Copies anything in a local .env into system properties so Spring's ${...}
     * placeholders resolve during development. Previously only the three Twilio
     * keys were copied, which meant the rest of .env was silently ignored.
     * Real environment variables always win - we never overwrite one that is
     * already set, so deployed environments are unaffected.
     */
    private static void loadDotenvIntoSystemProperties() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            if (System.getenv(entry.getKey()) == null
                && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Runs the app on Oman time (APP_TIMEZONE overrides). Every saved time
     * (ticket times, daily numbering, the daily sign-out) uses the JVM's zone,
     * which comes from how the process is started, not the server's clock:
     * the deployed backend ran on UTC+3 and printed 06:38 on 09:38 tickets.
     */
    private static void useBranchTimeZone() {
        String zone = System.getenv("APP_TIMEZONE");
        if (zone == null || zone.isBlank()) zone = System.getProperty("APP_TIMEZONE", "Asia/Muscat");
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));
        log.info("Time zone: {}", zone);
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        log.info("Server started on port {}", event.getWebServer().getPort());
    }
}
