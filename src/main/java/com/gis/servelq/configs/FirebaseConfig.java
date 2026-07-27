package com.gis.servelq.configs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FirebaseApp was never initialised anywhere, so every
 * FirebaseMessaging.getInstance() threw IllegalStateException and FCMService
 * swallowed it in a bare catch. Push notifications have therefore never worked -
 * silently. This wires it up, and reports clearly when it is off.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @PostConstruct
    void init() {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("firebase.credentials-path is not set - push notifications are disabled");
            return;
        }

        Path path = Paths.get(credentialsPath);
        if (!Files.isReadable(path)) {
            log.error("Firebase credentials file not readable at {} - push notifications are disabled",
                    path.toAbsolutePath());
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (InputStream in = new FileInputStream(path.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase initialised - push notifications are enabled");
        } catch (Exception e) {
            // Deliberately not fatal: a missing or bad key should not stop the
            // queue from running, but it must be loud in the logs.
            log.error("Failed to initialise Firebase - push notifications are disabled", e);
        }
    }
}
