package com.gis.servelq.services;

import com.gis.servelq.dto.LowScoringUserDTO;
import com.gis.servelq.dto.ResponseReceivedDTO;
import com.gis.servelq.dto.SurveySubmissionRequest;
import com.gis.servelq.dto.UserResponseDTO;
import com.gis.servelq.models.QuizSurveyModel;
import com.gis.servelq.models.ResponseModel;
import com.gis.servelq.models.User;
import com.gis.servelq.repository.QuizSurveyRepository;
import com.gis.servelq.repository.ResponseRepo;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.utils.ScoringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResponseService {

    private final QuizSurveyRepository quizSurveyRepo;
    private final ResponseRepo responseRepo;
    private final UserRepository userRepository;

    /* =====================================================
       SUBMIT RESPONSE
       ===================================================== */
    public ResponseModel storeResponse(UUID quizSurveyId, SurveySubmissionRequest request) {

        User user =
                userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new RuntimeException("Invalid userId"));

        QuizSurveyModel quiz =
                quizSurveyRepo.findById(quizSurveyId)
                        .orElseThrow(() -> new IllegalArgumentException("Quiz or Survey not found"));

        // Ensure user is targeted
        if (quiz.getTargetedUsers() == null ||
                !quiz.getTargetedUsers().contains(user.getId())) {
            throw new IllegalArgumentException("User not allowed to submit this quiz/survey");
        }

        List<ResponseModel> existing =
                responseRepo.findByQuizSurveyIdAndUserId(quizSurveyId, user.getId());

        // Survey: allow only one submission
        if ("survey".equalsIgnoreCase(quiz.getType()) && !existing.isEmpty()) {
            throw new IllegalStateException("Survey already submitted");
        }

        // Quiz: respect max retake. This is a per-user limit, so it is checked
        // against this user's own attempt count.
        //
        // There used to be a "quiz.setMaxRetake(getMaxRetake() - 1)" plus a save
        // right after this check. maxRetake is a single column on the shared
        // quiz row, so every submission by anyone decremented the same counter:
        // with maxRetake=3 and 100 targeted staff, the first 3 submissions in
        // total took it to 0 and locked out all 97 people who had not started.
        // It also rewrote both large jsonb columns on every submit just to
        // change one integer. The per-user check below is the whole rule.
        if ("quiz".equalsIgnoreCase(quiz.getType())
                && quiz.getMaxRetake() != null
                && existing.size() >= quiz.getMaxRetake()) {
            throw new IllegalStateException("Max quiz attempts exceeded");
        }

        return handleQuizResponse(quiz, request, user);
    }

    /* =====================================================
       QUIZ RESPONSE
       ===================================================== */

    private ResponseModel handleQuizResponse(
            QuizSurveyModel quiz,
            SurveySubmissionRequest request,
            User user
    ) {

        Map<String, String> questionTypes = new HashMap<>();
        Map<String, Integer> marks = new HashMap<>();

        quiz.getDefinitionJson().getPages().forEach(page ->
                page.getElements().forEach(el -> {
                    questionTypes.put(el.getName(), el.getType());
                    marks.put(el.getName(), el.getMarks() != null ? el.getMarks() : 1);
                })
        );

        ScoringUtil.ScoringResult result =
                ScoringUtil.score(
                        request.getAnswers(),
                        quiz.getAnswerKey(),
                        questionTypes,
                        marks
                );

        return responseRepo.save(
                ResponseModel.builder()
                        .quizSurveyId(quiz.getId())
                        .userId(user.getId())
                        .username(user.getName())
                        .answers(request.getAnswers())
                        .score(result.score())
                        .maxScore(quiz.getMaxScore())
                        .finishTimeMs(request.getFinishTime())
                        .build()
        );
    }

    /* =====================================================
       USER RESPONSES
       ===================================================== */
    public List<ResponseModel> getAllResponsesByUserId(String userId) {
        return responseRepo.findByUserId(userId);
    }

    /* =====================================================
       STAFF INVITED
       ===================================================== */
    public List<UserResponseDTO> totalStaffInvited(UUID quizSurveyId) {

        QuizSurveyModel quiz =
                quizSurveyRepo.findById(quizSurveyId)
                        .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        return userRepository.findAllById(quiz.getTargetedUsers())
                .stream()
                .map(UserResponseDTO::new)
                .toList();
    }


    /* =====================================================
       RESPONSES RECEIVED
       ===================================================== */
    public List<ResponseReceivedDTO> totalResponseReceived(UUID quizSurveyId) {

        QuizSurveyModel quiz =
                quizSurveyRepo.findById(quizSurveyId)
                        .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        Set<String> visibleFields = quiz.getUserDataDisplayFields();

        List<ResponseModel> responses =
                responseRepo.findByQuizSurveyId(quizSurveyId);

        Map<String, ResponseModel> bestAttempt =
                responses.stream()
                        .filter(r -> r.getScore() != null)
                        .collect(Collectors.toMap(
                                ResponseModel::getUserId,
                                r -> r,
                                (a, b) -> a.getScore() >= b.getScore() ? a : b
                        ));

        return bestAttempt.values()
                .stream()
                .map(r -> {
                    User user =
                            userRepository.findById(r.getUserId()).orElse(null);

                    if (user == null) return null;

                    String result =
                            r.getScore() >= (0.5 * r.getMaxScore())
                                    ? "PASS"
                                    : "FAIL";

                    ResponseReceivedDTO.ResponseReceivedDTOBuilder b =
                            ResponseReceivedDTO.builder()
                                    .id(user.getId())
                                    .result(result)
                                    .submittedAt(r.getSubmittedAt());

                    if (visibleFields.contains("username")) b.username(user.getName());
                    if (visibleFields.contains("role")) b.role(user.getRole());

                    return b.build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /* =====================================================
       LOW SCORERS (JPA SAFE)
       ===================================================== */

    /**
     * Users whose average score over the window is below the threshold.
     *
     * This used to be responseRepo.findAll() - every response row for every quiz
     * ever recorded, each one deserialising its jsonb answers blob that this
     * report never looks at - filtered by date afterwards in Java. Now the date
     * filter and the null guards run in the query, and only userId/score/maxScore
     * come back. The averaging stays in Java so the arithmetic is unchanged: it
     * is the mean of per-attempt percentages, not total score over total max.
     */
    public List<LowScoringUserDTO> getLowScoringUsers(int weeks, double thresholdPercent) {

        Instant fromDate = Instant.now().minus(weeks * 7L, ChronoUnit.DAYS);

        Map<String, List<double[]>> byUser = new LinkedHashMap<>();
        for (Object[] row : responseRepo.findScoresSince(fromDate)) {
            String userId = (String) row[0];
            double score = ((Number) row[1]).doubleValue();
            double maxScore = ((Number) row[2]).doubleValue();
            if (maxScore <= 0) {
                // Would have produced Infinity and silently dropped out of the
                // comparison below; skip it explicitly instead.
                continue;
            }
            byUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(new double[]{score, maxScore});
        }

        if (byUser.isEmpty()) {
            return List.of();
        }

        Map<String, String> namesById = userRepository.findAllById(byUser.keySet()).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        List<LowScoringUserDTO> result = new ArrayList<>();
        byUser.forEach((userId, attempts) -> {
            double avg = attempts.stream()
                    .mapToDouble(a -> (a[0] * 100.0) / a[1])
                    .average()
                    .orElse(0);

            if (avg < thresholdPercent) {
                result.add(LowScoringUserDTO.builder()
                        .userId(userId)
                        .username(namesById.get(userId))
                        .avgPercentage(avg)
                        .attemptCount(attempts.size())
                        .build());
            }
        });

        return result;
    }
}
