package com.gis.servelq;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EntityScan(basePackages = "com.gis.servelq.models")
@EnableJpaRepositories(basePackages = "com.gis.servelq.repository")
public class ServelqApplication implements ApplicationListener<WebServerInitializedEvent> {

    public static void main(String[] args) {
        loadDotenvIntoSystemProperties();
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

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        log.info("Server started on port {}", event.getWebServer().getPort());
    }
}
