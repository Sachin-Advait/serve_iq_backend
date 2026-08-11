package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.TrainingEngagementDTO;
import com.gis.servelq.dto.TrainingUploadAssignDTO;
import com.gis.servelq.dto.UserTrainingDTO;
import com.gis.servelq.models.TrainingAssignment;
import com.gis.servelq.models.TrainingMaterial;
import com.gis.servelq.security.AuthenticatedUser;
import com.gis.servelq.services.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/user/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;

    // ================= ADMIN =================
    @PostMapping
    public ResponseEntity<ApiResponseDTO<TrainingMaterial>> uploadTraining(
            @RequestBody TrainingUploadAssignDTO request,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        TrainingMaterial savedMaterial = trainingService.uploadAndAssign(request, admin);

        return ResponseEntity.ok(
                new ApiResponseDTO<>(true, "Training uploaded and assigned successfully", savedMaterial)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<TrainingMaterial>>> getAllTrainings() {
        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        true,
                        "All trainings fetched successfully",
                        trainingService.getAllMaterials()
                )
        );
    }

    @PostMapping("/assign")
    public ResponseEntity<ApiResponseDTO<Void>> assignTraining(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        Long trainingId = Long.valueOf(payload.get("trainingId").toString());
        List<String> userIds = (List<String>) payload.get("userIds");
        Instant dueDate = Instant.parse(payload.get("dueDate").toString());

        trainingService.assignTraining(trainingId, userIds, dueDate, admin);
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Assigned", null));
    }

    // ================= USER =================
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponseDTO<List<TrainingAssignment>>> getUserTrainings(@PathVariable String userId) {
        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        true,
                        "User trainings fetched successfully",
                        trainingService.getUserTrainings(userId)
                )
        );
    }

    @GetMapping("/user/{userId}/details")
    public ResponseEntity<ApiResponseDTO<List<UserTrainingDTO>>> getUserTrainingDetails(@PathVariable String userId) {
        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        true,
                        "User training details fetched successfully",
                        trainingService.getUserTrainingDetails(userId)
                )
        );
    }

    @PostMapping("/progress")
    public ResponseEntity<ApiResponseDTO<TrainingAssignment>> updateProgress(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        String userId = payload.get("userId").toString();
        Long trainingId = Long.valueOf(payload.get("trainingId").toString());
        Integer progress = Integer.valueOf(payload.get("progress").toString());

        TrainingAssignment updated =
                trainingService.updateProgress(userId, trainingId, progress, currentUser);

        return ResponseEntity.ok(
                new ApiResponseDTO<>(true, "Training progress updated successfully", updated)
        );
    }

    @GetMapping("/engagement")
    public ResponseEntity<ApiResponseDTO<List<TrainingEngagementDTO>>> getEngagement(
            @RequestParam(required = false) Long trainingId) {
        return ResponseEntity.ok(
                new ApiResponseDTO<>(
                        true,
                        "Engagement fetched successfully",
                        trainingService.getEngagement(trainingId)
                )
        );
    }

    // ================= ADMIN =================

    @PutMapping("/{trainingId}")
    public ResponseEntity<ApiResponseDTO<TrainingMaterial>> updateTraining(
            @PathVariable Long trainingId,
            @RequestBody TrainingUploadAssignDTO request,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        TrainingMaterial updated =
                trainingService.updateTraining(trainingId, request, admin);

        return ResponseEntity.ok(
                new ApiResponseDTO<>(true, "Training updated successfully", updated)
        );
    }

    @DeleteMapping("/{trainingId}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteTraining(
            @PathVariable Long trainingId,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        trainingService.deleteTraining(trainingId, admin);

        return ResponseEntity.ok(
                new ApiResponseDTO<>(true, "Training deleted successfully", null)
        );
    }
}