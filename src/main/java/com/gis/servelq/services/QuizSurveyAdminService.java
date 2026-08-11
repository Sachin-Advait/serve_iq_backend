package com.gis.servelq.services;

import com.gis.servelq.controllers.QuizSurveySocketController;
import com.gis.servelq.dto.QuizCompletionStatsDTO;
import com.gis.servelq.dto.QuizInsightsDTO;
import com.gis.servelq.models.AnnouncementMode;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.QuizSurveyModel;
import com.gis.servelq.models.ResponseModel;
import com.gis.servelq.repository.QuizSurveyRepository;
import com.gis.servelq.repository.ResponseRepo;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizSurveyAdminService {

    private final QuizSurveyRepository quizSurveyRepo;
    private final ResponseRepo responseRepo;
    private final QuizSurveySocketController socketController;
    private final FCMService fcmService;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    /* ---------------- CREATE ---------------- */
    public QuizSurveyModel create(QuizSurveyModel model, AuthenticatedUser admin) {
        model.setIsAnnounced(
                model.getAnnouncementMode() == AnnouncementMode.IMMEDIATE
        );

        QuizSurveyModel saved = quizSurveyRepo.save(model);

        // Log quiz created
        auditLogService.log(
                AuditAction.QUIZ_CREATED,
                "QuizSurvey",
                saved.getId().toString(),
                saved.getTitle(),
                "Quiz/Survey created: " + saved.getTitle() + " (" + saved.getType() + ")",
                admin,
                null,
                request
        );

        if (saved.getIsAnnounced()) {
            pushNotifications(saved);
        }

        return saved;
    }

    /* ---------------- UPDATE ---------------- */
    public QuizSurveyModel update(UUID id, QuizSurveyModel updated, AuthenticatedUser admin) {

        QuizSurveyModel existing = quizSurveyRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getQuizDuration() != null) existing.setQuizDuration(updated.getQuizDuration());
        if (updated.getQuizTotalDuration() != null) existing.setQuizTotalDuration(updated.getQuizTotalDuration());
        if (updated.getIsMandatory() != null) existing.setIsMandatory(updated.getIsMandatory());
        if (updated.getVisibilityType() != null) existing.setVisibilityType(updated.getVisibilityType());
        if (updated.getTargetedUsers() != null) existing.setTargetedUsers(updated.getTargetedUsers());

        if (updated.getUserDataDisplayFields() != null)
            existing.setUserDataDisplayFields(updated.getUserDataDisplayFields());

        if (updated.getAnnouncementMode() != null) existing.setAnnouncementMode(updated.getAnnouncementMode());
        if (updated.getScheduledTime() != null) existing.setScheduledTime(updated.getScheduledTime());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());

        QuizSurveyModel saved = quizSurveyRepo.save(existing);

        // Log quiz updated
        auditLogService.log(
                AuditAction.QUIZ_UPDATED,
                "QuizSurvey",
                saved.getId().toString(),
                saved.getTitle(),
                "Quiz/Survey updated: " + saved.getTitle(),
                admin,
                null,
                request
        );

        return saved;
    }

    /* ---------------- DELETE ---------------- */
    public void delete(UUID id, AuthenticatedUser admin) {
        QuizSurveyModel quiz = quizSurveyRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        // Log before deletion
        auditLogService.log(
                AuditAction.QUIZ_DELETED,
                "QuizSurvey",
                quiz.getId().toString(),
                quiz.getTitle(),
                "Quiz/Survey deleted: " + quiz.getTitle(),
                admin,
                null,
                request
        );

        quizSurveyRepo.deleteById(id);
    }

    /* ---------------- LIST ALL ---------------- */
    public List<QuizSurveyModel> getAll() {
        return quizSurveyRepo.findAll();
    }

    /* ---------------- MANUAL ANNOUNCE ---------------- */
    public void manualAnnounce(UUID quizId, AuthenticatedUser admin) {
        QuizSurveyModel quiz = quizSurveyRepo.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (quiz.getIsAnnounced()) return;

        quiz.setIsAnnounced(true);
        quizSurveyRepo.save(quiz);

        // Log manual announcement
        auditLogService.log(
                AuditAction.QUIZ_ANNOUNCED,
                "QuizSurvey",
                quiz.getId().toString(),
                quiz.getTitle(),
                "Quiz/Survey manually announced: " + quiz.getTitle(),
                admin,
                null,
                request
        );

        pushNotifications(quiz);
    }

    /* ---------------- RESPONSES ---------------- */
    public List<ResponseModel> getResponses(UUID quizId) {
        return responseRepo.findByQuizSurveyId(quizId);
    }

    /* ---------------- COMMON PUSH ---------------- */
    private void pushNotifications(QuizSurveyModel quiz) {
        socketController.pushNewSurvey(
                quiz.getId().toString(),
                quiz.getIsMandatory(),
                new ArrayList<>(quiz.getTargetedUsers())
        );

        fcmService.notifyQuizSurveyAssigned(quiz);
    }

    public QuizInsightsDTO getQuizInsights(UUID quizSurveyId) {

        QuizSurveyModel quiz = quizSurveyRepo.findById(quizSurveyId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        List<ResponseModel> responses = responseRepo.findByQuizSurveyId(quizSurveyId);

        if (responses.isEmpty()) {
            return QuizInsightsDTO.builder()
                    .title(quiz.getTitle())
                    .averageScore(0)
                    .passRate(0)
                    .failRate(0)
                    .build();
        }

        int maxScore = quiz.getMaxScore();
        double passMark = maxScore * 0.5;

        double avgScore = responses.stream().filter(r -> r.getScore() != null)
                .mapToInt(ResponseModel::getScore).average().orElse(0);

        long passed = responses.stream()
                .filter(r -> r.getScore() != null && r.getScore() >= passMark).count();

        long failed = responses.size() - passed;

        ResponseModel top = responses.stream().filter(r -> r.getScore() != null)
                .max((a, b) -> a.getScore().compareTo(b.getScore())).orElse(null);

        ResponseModel lowest = responses.stream().filter(r -> r.getScore() != null)
                .min((a, b) -> a.getScore().compareTo(b.getScore())).orElse(null);

        return QuizInsightsDTO.builder()
                .title(quiz.getTitle())
                .averageScore(avgScore)
                .passRate((passed * 100.0) / responses.size())
                .failRate((failed * 100.0) / responses.size())
                .topScorer(top == null ? null : QuizInsightsDTO.ScorerDTO.builder().name(top.getUsername())
                        .score(top.getScore()).build())
                .lowestScorer(lowest == null ? null : QuizInsightsDTO.ScorerDTO.builder().name(lowest.getUsername())
                        .score(lowest.getScore()).build()
                )
                .mostIncorrectQuestions(List.of()) // optional enhancement
                .build();
    }


    public QuizCompletionStatsDTO getQuizCompletionStats(UUID quizSurveyId) {
        QuizSurveyModel quiz = quizSurveyRepo.findById(quizSurveyId)
                .orElseThrow(() -> new RuntimeException("Quiz not found"));

        int totalAssigned = quiz.getTargetedUsers() == null ? 0 : quiz.getTargetedUsers().size();

        int totalCompleted = (int) responseRepo.findByQuizSurveyId(quizSurveyId).stream()
                .map(ResponseModel::getUserId).distinct().count();


        int totalNotCompleted = totalAssigned - totalCompleted;
        double completionRate = totalAssigned == 0 ? 0 : (totalCompleted * 100.0) / totalAssigned;

        return new QuizCompletionStatsDTO(totalAssigned, totalCompleted, Math.max(totalNotCompleted, 0), completionRate);
    }
}