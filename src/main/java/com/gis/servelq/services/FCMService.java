package com.gis.servelq.services;

import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.QuizSurveyModel;
import com.gis.servelq.models.User;
import com.gis.servelq.repository.UserRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FCMService {

    /** FCM caps sendEachForMulticast at 500 tokens per call. */
    private static final int MULTICAST_BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public void sendNotification(String token, String title, String body, String category, String contentId) {
        if (notConfigured()) {
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("category", category)
                    .putData("contentId", contentId)
                    .build();

            log.debug("FCM sent: {}", FirebaseMessaging.getInstance().send(message));
        } catch (Exception e) {
            log.error("Error sending FCM notification", e);
        }
    }

    /* ================= TRAINING ================= */
    @Async
    public void notifyTrainingAssigned(String trainingId, List<String> userIds) {
        sendToUsers(
                userIds,
                "New Training Assigned",
                "A new training has been assigned to you. Please complete it before the due date.",
                "TRAINING",
                trainingId);

        // Log FCM sent
        auditLogService.log(
                AuditAction.FCM_SENT,
                "FCM",
                trainingId,
                "Training",
                "FCM notification sent for training " + trainingId + " to " + userIds.size() + " users",
                null,
                null,
                null
        );
    }

    /* ================= QUIZ / SURVEY ================= */
    @Async
    public void notifyQuizSurveyAssigned(QuizSurveyModel quiz) {
        if (quiz.getTargetedUsers() == null || quiz.getTargetedUsers().isEmpty()) {
            return;
        }

        boolean isQuiz = "Quiz".equalsIgnoreCase(quiz.getType());
        sendToUsers(
                quiz.getTargetedUsers(),
                isQuiz ? "New Quiz Assigned" : "New Survey Assigned",
                "A new " + quiz.getType().toLowerCase() + " has been assigned to you: " + quiz.getTitle(),
                "QUIZ",
                quiz.getId().toString());

        // Log FCM sent
        auditLogService.log(
                AuditAction.FCM_SENT,
                "FCM",
                quiz.getId().toString(),
                quiz.getTitle(),
                "FCM notification sent for " + quiz.getType() + " to " +
                        (quiz.getTargetedUsers() != null ? quiz.getTargetedUsers().size() : 0) + " users",
                null,
                null,
                null
        );
    }

    /**
     * Both notify methods used to loop the user ids doing one findById and one
     * blocking send each, so assigning to 50 people meant 50 selects and 50
     * serial network calls on the caller's thread. This is one query for the
     * users and one multicast per 500 tokens.
     */
    private void sendToUsers(Collection<String> userIds, String title, String body,
                             String category, String contentId) {
        if (notConfigured() || userIds == null || userIds.isEmpty()) {
            return;
        }

        List<String> tokens = userRepository.findAllById(userIds).stream()
                .map(User::getFcmToken)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            log.debug("No device tokens registered for {} target user(s)", userIds.size());
            return;
        }

        for (int start = 0; start < tokens.size(); start += MULTICAST_BATCH_SIZE) {
            List<String> batch = tokens.subList(
                    start, Math.min(start + MULTICAST_BATCH_SIZE, tokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(batch)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putData("category", category)
                        .putData("contentId", contentId)
                        .build();

                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                if (response.getFailureCount() > 0) {
                    log.warn("FCM multicast: {} sent, {} failed",
                            response.getSuccessCount(), response.getFailureCount());
                } else {
                    log.debug("FCM multicast: {} sent", response.getSuccessCount());
                }
            } catch (Exception e) {
                log.error("Error sending FCM multicast to {} device(s)", batch.size(), e);
            }
        }
    }

    /**
     * Firebase is optional. Without this guard FirebaseMessaging.getInstance()
     * throws IllegalStateException when no app is initialised, which is exactly
     * what was happening on every push before FirebaseConfig existed.
     */
    private boolean notConfigured() {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase is not configured - skipping push notification");
            return true;
        }
        return false;
    }
}