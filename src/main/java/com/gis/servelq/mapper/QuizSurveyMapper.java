package com.gis.servelq.mapper;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.servelq.dto.QuizzesSurveysDTO;
import com.gis.servelq.models.QuizSurveyModel;
import com.gis.servelq.models.SurveyDefinition;
import com.gis.servelq.repository.ResponseRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
@RequiredArgsConstructor

public class QuizSurveyMapper {

    private final ResponseRepo responseRepo;
    public QuizzesSurveysDTO mapToDtoWithoutUser(QuizSurveyModel quiz) {
        return QuizzesSurveysDTO.builder()
                .id(quiz.getId())
                .type(quiz.getType())
                .title(quiz.getTitle())
                .totalQuestion(getQuestionCount(quiz))
                .status(quiz.getStatus())
                .quizTotalDuration(quiz.getQuizTotalDuration())
                .isAnnounced(quiz.getIsAnnounced())
                .createdAt(quiz.getCreatedAt())
                .maxRetake(quiz.getMaxRetake())
                .userDataDisplayFields(quiz.getUserDataDisplayFields() != null ?
                        new HashSet<>(quiz.getUserDataDisplayFields()) : new HashSet<>())
                .visibilityType(quiz.getVisibilityType())
                .build();
    }

    public QuizzesSurveysDTO mapToDtoWithUser(QuizSurveyModel quiz, String userId) {

        boolean isParticipated =
                responseRepo.hasUserParticipated(quiz.getId(), userId);

        boolean isMandatory =
                !isParticipated && Boolean.TRUE.equals(quiz.getIsMandatory());

        return QuizzesSurveysDTO.builder()
                .id(quiz.getId())
                .type(quiz.getType())
                .title(quiz.getTitle())
                .totalQuestion(getQuestionCount(quiz))
                .status(quiz.getStatus())
                .quizTotalDuration(quiz.getQuizTotalDuration())
                .quizDuration(quiz.getQuizDuration())
                .isAnnounced(quiz.getIsAnnounced())
                .createdAt(quiz.getCreatedAt())
                .isParticipated(isParticipated)
                .isMandatory(isMandatory)
                .maxRetake(quiz.getMaxRetake())
                .userDataDisplayFields(quiz.getUserDataDisplayFields() != null ?
                        new HashSet<>(quiz.getUserDataDisplayFields()) : new HashSet<>())
                .visibilityType(quiz.getVisibilityType())
                .build();
    }

    private int getQuestionCount(QuizSurveyModel quiz) {

        SurveyDefinition definition = quiz.getDefinitionJson();

        if (definition == null
                || definition.getPages() == null
                || definition.getPages().isEmpty()
                || definition.getPages().get(0).getElements() == null) {
            return 0;
        }

        return definition.getPages().get(0).getElements().size();
    }
}
