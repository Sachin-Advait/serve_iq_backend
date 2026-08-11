package com.gis.servelq.services;

import com.gis.servelq.dto.PageResponseDTO;
import com.gis.servelq.dto.QuizScoreSummaryDTO;
import com.gis.servelq.dto.QuizSurveyDTO;
import com.gis.servelq.dto.QuizzesSurveysDTO;
import com.gis.servelq.mapper.QuizSurveyMapper;
import com.gis.servelq.models.QuizSurveyModel;
import com.gis.servelq.models.ResponseModel;
import com.gis.servelq.repository.QuizSurveyRepository;
import com.gis.servelq.repository.ResponseRepo;
import com.gis.servelq.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizSurveyService {

    private final QuizSurveyRepository quizSurveyRepo;
    private final UserRepository userRepository;
    private final QuizSurveyMapper quizSurveyMapper;
    private final ResponseRepo responseRepo;
    private final FCMService fcmService;

    /* -------------------------------------------------
       GET QUIZ / SURVEY BY ID
    ------------------------------------------------- */
    public QuizSurveyDTO getQuizSurvey(UUID id) {
        QuizSurveyModel quiz = quizSurveyRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz or survey not found"));

        return QuizSurveyDTO.builder()
                .id(quiz.getId())
                .type(quiz.getType())
                .title(quiz.getTitle())
                .definitionJson(quiz.getDefinitionJson())
                .quizDuration(quiz.getQuizDuration())
                .quizTotalDuration(quiz.getQuizTotalDuration())
                .maxScore(quiz.getMaxScore())
                .answerKey(quiz.getAnswerKey())
                .createdAt(quiz.getCreatedAt())
                .maxRetake(quiz.getMaxRetake())
                .visibilityType(quiz.getVisibilityType())
                .announcementMode(quiz.getAnnouncementMode())
                .userDataDisplayFields(quiz.getUserDataDisplayFields())
                .build();
    }

    /* -------------------------------------------------
       SPECIFICATION (FILTERS)
    ------------------------------------------------- */
    private Specification<QuizSurveyModel> buildSpecification(String userId, String status, String type, Instant startDate
    ) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Targeted user filter
            if (userId != null) {
                Join<QuizSurveyModel, String> users = root.join("targetedUsers");
                predicates.add(cb.equal(users, userId));
            }

            // Status filter
            if (status != null && !"All Status".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), status.equalsIgnoreCase("Active")));
            }

            // Type filter
            if (type != null && !"All Types".equalsIgnoreCase(type)) {
                predicates.add(cb.equal(cb.lower(root.get("type")), type.toLowerCase()));
            }

            // Date filter
            if (startDate != null) {
                predicates.add(cb.between(root.get("createdAt"), startDate,
                        startDate.plus(1, ChronoUnit.DAYS)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /* -------------------------------------------------
       FIND WITH FILTERS (JPA ONLY)
    ------------------------------------------------- */
    @Transactional(readOnly = true)
    public Page<QuizSurveyModel> findWithFilters(
            String userId,
            String status,
            String type,
            String participation,
            Instant startDate,
            Pageable pageable
    ) {

        if (userId != null) {
            userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Invalid username"));
        }

        Specification<QuizSurveyModel> spec = buildSpecification(userId, status, type, startDate);

        Page<QuizSurveyModel> page = quizSurveyRepo.findAll(spec, pageable);

        // Participation filter (post-fetch)
        if (userId != null && participation != null && !"All".equalsIgnoreCase(participation)) {

            Set<UUID> participatedQuizIds =
                    responseRepo.findByUserId(userId)
                            .stream()
                            .map(ResponseModel::getQuizSurveyId)
                            .collect(Collectors.toSet());

            List<QuizSurveyModel> filtered =
                    page.getContent().stream()
                            .filter(q ->
                                    participation.equalsIgnoreCase("Participated")
                                            ? participatedQuizIds.contains(q.getId())
                                            : !participatedQuizIds.contains(q.getId()))
                            .toList();

            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        return page;
    }

    /* -------------------------------------------------
       MAIN LIST API
    ------------------------------------------------- */
    @Transactional(readOnly = true)
    public PageResponseDTO<QuizzesSurveysDTO> getQuizzesSurveys(
            String userId,
            String status,
            String type,
            String sort,
            String participation,
            Instant startDate,
            int page,
            int size
    ) {

        Sort.Direction direction =
                "Oldest".equalsIgnoreCase(sort)
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"));

        Page<QuizSurveyModel> quizzes =
                findWithFilters(userId, status, type, participation, startDate, pageable);

        Page<QuizzesSurveysDTO> dtoPage = quizzes.map(q ->
                userId == null ? quizSurveyMapper.mapToDtoWithoutUser(q)
                        : quizSurveyMapper.mapToDtoWithUser(q, userId));

        return new PageResponseDTO<>(dtoPage);
    }


    /* -------------------------------------------------
       QUIZ SCORE SUMMARY
    ------------------------------------------------- */
    public QuizScoreSummaryDTO quizScoreSummary(UUID quizSurveyId) {

        List<ResponseModel> responses =
                responseRepo.findByQuizSurveyId(quizSurveyId);

        if (responses.isEmpty()) {
            throw new IllegalArgumentException("No responses found");
        }

        Map<String, ResponseModel> bestAttempts =
                responses.stream()
                        .filter(r -> r.getScore() != null)
                        .collect(Collectors.toMap(
                                ResponseModel::getUserId,
                                r -> r,
                                (a, b) -> a.getScore() >= b.getScore() ? a : b
                        ));

        Collection<ResponseModel> unique = bestAttempts.values();

        int totalAttempts = unique.size();
        int totalScore = unique.stream().mapToInt(ResponseModel::getScore).sum();
        int highestScore = unique.stream().mapToInt(ResponseModel::getScore).max().orElse(0);
        int maxScore = unique.stream().mapToInt(ResponseModel::getMaxScore).max().orElse(0);

        double avgScore =
                totalAttempts == 0 ? 0 : (double) totalScore / totalAttempts;

        List<Map<String, Object>> topScorers =
                unique.stream()
                        .sorted(Comparator
                                .comparing(ResponseModel::getScore).reversed()
                                .thenComparing(
                                        ResponseModel::getFinishTimeMs,
                                        Comparator.nullsLast(Long::compareTo)
                                ).thenComparing(ResponseModel::getSubmittedAt))
                        .limit(3)
                        .map(r -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("username", r.getUsername());
                            map.put("userId", r.getUserId());
                            map.put("score", r.getScore());
                            map.put("maxScore", r.getMaxScore());
                            map.put("finishTimeMs", r.getFinishTimeMs());
                            map.put("submittedAt", r.getSubmittedAt());
                            return map;
                        })
                        .toList();

        return new QuizScoreSummaryDTO(
                totalAttempts,
                avgScore,
                highestScore,
                maxScore,
                topScorers
        );
    }

    @Transactional(readOnly = true)
    public List<QuizzesSurveysDTO> getQuizzesByTargetUser(String userId) {
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Invalid user id"));
        return quizSurveyRepo.findByTargetedUser(userId).stream()
                .map(q -> quizSurveyMapper.mapToDtoWithUser(q, userId)).toList();
    }

}
