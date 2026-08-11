package com.gis.servelq.controllers;

import com.gis.servelq.dto.*;
import com.gis.servelq.models.ResponseModel;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.QuizSurveyService;
import com.gis.servelq.services.ResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/serveiq/api/user/quiz-survey")
@RequiredArgsConstructor
public class QuizSurveyController {

    private final QuizSurveyService quizSurveyService;
    private final ResponseService responseService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDTO<QuizSurveyDTO>> getQuizSurvey(@PathVariable UUID id) {
        QuizSurveyDTO quizSurvey = quizSurveyService.getQuizSurvey(id);
        if (quizSurvey == null) {
            return ResponseEntity.status(404).body(
                    new ApiResponseDTO<>(false, "Quiz & survey not found", null)
            );
        }
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Quiz & survey retrieved successfully", quizSurvey));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<QuizzesSurveysDTO>>> getQuizzesSurveys(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "All Status") String status,
            @RequestParam(defaultValue = "All Types") String type,
            @RequestParam(defaultValue = "Latest") String sort,
            @RequestParam(defaultValue = "All") String participation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponseDTO<QuizzesSurveysDTO> surveys = quizSurveyService.getQuizzesSurveys(userId, status, type, sort, participation, startDate, page, size);
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "All quiz & surveys retrieved successfully", surveys));
    }

    @GetMapping("/summary/{quizSurveyId}")
    public ResponseEntity<ApiResponseDTO<QuizScoreSummaryDTO>> getSummary(@PathVariable UUID quizSurveyId) {

        QuizScoreSummaryDTO response = quizSurveyService.quizScoreSummary(quizSurveyId);
        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Response submitted successfully", response));
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponseDTO<List<QuizzesSurveysDTO>>> getByTargetUser(@PathVariable String userId) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Quizzes fetched for user",
                quizSurveyService.getQuizzesByTargetUser(userId))
        );
    }

    /* ---------------- SUBMIT RESPONSE ---------------- */
    @PostMapping("/user/submit/{quizSurveyId}")
    public ResponseEntity<ApiResponseDTO<ResponseModel>> submitResponse(
            @PathVariable UUID quizSurveyId,
            @RequestBody SurveySubmissionRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        ResponseModel response = responseService.storeResponse(quizSurveyId, request, currentUser);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Response submitted successfully", response));
    }

    /* ---------------- USER RESPONSES ---------------- */
    @GetMapping("/user/responses/by-user/{userId}")
    public ResponseEntity<ApiResponseDTO<List<ResponseModel>>> getAllResponsesByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Responses fetched successfully",
                responseService.getAllResponsesByUserId(userId)));
    }

    /* ---------------- STAFF INVITED ---------------- */
    @GetMapping("/user/responses/staff-invited/{quizSurveyId}")
    public ResponseEntity<ApiResponseDTO<List<UserResponseDTO>>> totalStaffInvited(
            @PathVariable UUID quizSurveyId
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Staff invited retrieved successfully",
                responseService.totalStaffInvited(quizSurveyId))
        );
    }

    /* ---------------- RESPONSES RECEIVED ---------------- */
    @GetMapping("/user/responses/response-received/{quizSurveyId}")
    public ResponseEntity<ApiResponseDTO<List<ResponseReceivedDTO>>> totalResponseReceived(
            @PathVariable UUID quizSurveyId
    ) {
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Respondents retrieved successfully",
                responseService.totalResponseReceived(quizSurveyId))
        );
    }

    /* ---------------- LOW SCORERS ---------------- */
    @GetMapping("/user/responses/low-scorers")
    public ResponseEntity<ApiResponseDTO<List<LowScoringUserDTO>>> lowScorersLast5Weeks() {

        return ResponseEntity.ok(new ApiResponseDTO<>(true,
                "Low scorers during last 5 weeks retrieved successfully",
                responseService.getLowScoringUsers(5, 50))
        );
    }

}