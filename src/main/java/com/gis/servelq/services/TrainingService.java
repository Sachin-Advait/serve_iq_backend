package com.gis.servelq.services;

import com.gis.servelq.dto.TrainingEngagementDTO;
import com.gis.servelq.dto.TrainingUploadAssignDTO;
import com.gis.servelq.dto.UserTrainingDTO;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.TrainingAssignment;
import com.gis.servelq.models.TrainingMaterial;
import com.gis.servelq.models.User;
import com.gis.servelq.repository.TrainingAssignmentRepository;
import com.gis.servelq.repository.TrainingMaterialRepository;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingMaterialRepository materialRepo;
    private final TrainingAssignmentRepository assignmentRepo;
    private final UserRepository userRepo;
    private final FCMService fcmService;
    private final AuditLogService auditLogService;
    private final HttpServletRequest request;

    /* ======================================================
       ADMIN
       ====================================================== */

    public TrainingMaterial uploadAndAssign(TrainingUploadAssignDTO trainingRequest, AuthenticatedUser admin) {

        // BUILD material from FLAT DTO
        TrainingMaterial material = TrainingMaterial.builder()
                .title(trainingRequest.getTitle())
                .type(trainingRequest.getType())
                .duration(trainingRequest.getDuration())
                .cloudinaryPublicId(trainingRequest.getCloudinaryPublicId())
                .cloudinaryUrl(trainingRequest.getCloudinaryUrl())
                .cloudinaryResourceType(trainingRequest.getCloudinaryResourceType())
                .cloudinaryFormat(trainingRequest.getCloudinaryFormat())
                .assignedTo(0)
                .completionRate(0)
                .views(0)
                .active(true)
                .uploadDate(Instant.now())
                .build();

        TrainingMaterial savedMaterial = materialRepo.save(material);

        // Log training created
        auditLogService.log(
                AuditAction.TRAINING_CREATED,
                "Training",
                savedMaterial.getId().toString(),
                savedMaterial.getTitle(),
                "Training material uploaded: " + savedMaterial.getTitle(),
                admin,
                null,
                this.request
        );

        if (trainingRequest.getUserIds() != null && !trainingRequest.getUserIds().isEmpty()) {

            for (String userId : trainingRequest.getUserIds()) {

                boolean alreadyAssigned =
                        assignmentRepo.findByUserIdAndTrainingId(userId, savedMaterial.getId())
                                .isPresent();

                if (alreadyAssigned) continue;

                TrainingAssignment assignment = TrainingAssignment.builder()
                        .userId(userId)
                        .trainingId(savedMaterial.getId())
                        .progress(0)
                        .status("not-started")
                        .dueDate(trainingRequest.getDueDate())
                        .assignedAt(Instant.now())
                        .build();

                assignmentRepo.save(assignment);

                // Log training assigned
                auditLogService.log(
                        AuditAction.TRAINING_ASSIGNED,
                        "Training",
                        savedMaterial.getId().toString(),
                        savedMaterial.getTitle(),
                        "Training assigned to user: " + userId,
                        admin,
                        null,
                        this.request
                );
            }

            long totalAssigned = assignmentRepo.countByTrainingId(savedMaterial.getId());
            savedMaterial.setAssignedTo((int) totalAssigned);
            materialRepo.save(savedMaterial);
        }

        return savedMaterial;
    }

    public List<TrainingMaterial> getAllMaterials() {
        return materialRepo.findAllByActiveTrue();
    }

    public void assignTraining(Long trainingId, List<String> userIds, Instant dueDate, AuthenticatedUser admin) {

        TrainingMaterial material = materialRepo.findByIdAndActiveTrue(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));

        List<String> newlyAssigned = new ArrayList<>();

        for (String userId : userIds) {

            boolean alreadyAssigned =
                    assignmentRepo.findByUserIdAndTrainingId(userId, trainingId).isPresent();

            if (alreadyAssigned) continue;

            TrainingAssignment assignment = TrainingAssignment.builder()
                    .userId(userId)
                    .trainingId(trainingId)
                    .progress(0)
                    .status("not-started")
                    .dueDate(dueDate)
                    .assignedAt(Instant.now())
                    .build();

            assignmentRepo.save(assignment);
            newlyAssigned.add(userId);

            // Log training assigned
            auditLogService.log(
                    AuditAction.TRAINING_ASSIGNED,
                    "Training",
                    trainingId.toString(),
                    material.getTitle(),
                    "Training assigned to user: " + userId,
                    admin,
                    null,
                    request
            );
        }

        material.setAssignedTo((int) assignmentRepo.countByTrainingId(trainingId));
        materialRepo.save(material);

        if (!newlyAssigned.isEmpty()) {
            fcmService.notifyTrainingAssigned(trainingId.toString(), newlyAssigned);
        }
    }

    /* ======================================================
       USER
       ====================================================== */

    public List<TrainingAssignment> getUserTrainings(String userId) {
        return assignmentRepo.findByUserId(userId);
    }

    public List<UserTrainingDTO> getUserTrainingDetails(String userId) {

        List<TrainingAssignment> assignments = assignmentRepo.findByUserId(userId);

        return assignments.stream()
                .map(a -> {
                    TrainingMaterial material =
                            materialRepo.findByIdAndActiveTrue(a.getTrainingId()).orElse(null);

                    if (material == null) return null;

                    return new UserTrainingDTO(
                            a.getId(),
                            material.getId(),
                            material.getTitle(),
                            material.getType(),
                            material.getDuration(),
                            material.getCloudinaryUrl(),
                            material.getCloudinaryFormat(),
                            material.getCloudinaryResourceType(),
                            a.getProgress(),
                            a.getStatus(),
                            a.getDueDate()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public TrainingAssignment updateProgress(String userId, Long trainingId, int progress,
                                             AuthenticatedUser currentUser) {

        TrainingAssignment assignment =
                assignmentRepo.findByUserIdAndTrainingId(userId, trainingId)
                        .orElseThrow(() -> new RuntimeException("Training not assigned"));

        if (progress > assignment.getProgress()) {
            assignment.setProgress(progress);
        }

        if (assignment.getProgress() >= 100) {
            assignment.setStatus("completed");
        } else if (assignment.getProgress() > 0) {
            assignment.setStatus("in-progress");
        }

        assignmentRepo.save(assignment);

        if (assignment.getProgress() >= 100) {
            long total = assignmentRepo.countByTrainingId(trainingId);
            long completed = assignmentRepo.countByTrainingIdAndStatus(trainingId, "completed");

            materialRepo.findByIdAndActiveTrue(trainingId).ifPresent(material -> {
                int rate = total == 0 ? 0 : (int) ((completed * 100) / total);
                material.setCompletionRate(rate);
                materialRepo.save(material);
            });

            // Log training completed
            auditLogService.log(
                    AuditAction.TRAINING_COMPLETED,
                    "Training",
                    trainingId.toString(),
                    null,
                    "Training completed by user: " + userId,
                    currentUser,
                    null,
                    request
            );
        }

        return assignment;
    }

    public List<TrainingEngagementDTO> getEngagement(Long trainingId) {

        List<TrainingAssignment> assignments =
                trainingId != null
                        ? assignmentRepo.findByTrainingId(trainingId)
                        : assignmentRepo.findAll();

        return assignments.stream()
                .map(a -> {
                    User user = userRepo.findById(a.getUserId()).orElse(null);
                    TrainingMaterial material =
                            materialRepo.findByIdAndActiveTrue(a.getTrainingId()).orElse(null);

                    if (user == null || material == null) return null;

                    return TrainingEngagementDTO.builder()
                            .userId(user.getId())
                            .learner(user.getName())
                            .trainingId(material.getId())
                            .video(material.getTitle())
                            .progress(a.getProgress())
                            .status(a.getStatus())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /* ======================================================
       UPDATE / DELETE
       ====================================================== */

    public TrainingMaterial updateTraining(Long trainingId, TrainingUploadAssignDTO updateRequest,
                                           AuthenticatedUser admin) {

        TrainingMaterial material =
                materialRepo.findByIdAndActiveTrue(trainingId)
                        .orElseThrow(() -> new RuntimeException("Training not found"));

        if (updateRequest.getTitle() != null) material.setTitle(updateRequest.getTitle());
        if (updateRequest.getType() != null) material.setType(updateRequest.getType());
        if (updateRequest.getDuration() != null) material.setDuration(updateRequest.getDuration());

        if (updateRequest.getCloudinaryUrl() != null) {
            material.setCloudinaryUrl(updateRequest.getCloudinaryUrl());
            material.setCloudinaryPublicId(updateRequest.getCloudinaryPublicId());
            material.setCloudinaryResourceType(updateRequest.getCloudinaryResourceType());
            material.setCloudinaryFormat(updateRequest.getCloudinaryFormat());
        }

        materialRepo.save(material);

        // Log training updated
        auditLogService.log(
                AuditAction.TRAINING_UPDATED,
                "Training",
                material.getId().toString(),
                material.getTitle(),
                "Training updated: " + material.getTitle(),
                admin,
                null,
                request
        );

        /* ===== Assignment sync ===== */

        List<String> newUserIds = updateRequest.getUserIds() != null ? updateRequest.getUserIds() : List.of();
        List<TrainingAssignment> existing = assignmentRepo.findByTrainingId(trainingId);

        List<String> existingUserIds =
                existing.stream().map(TrainingAssignment::getUserId).toList();

        List<String> newlyAssigned = new ArrayList<>();

        for (String userId : newUserIds) {
            if (existingUserIds.contains(userId)) continue;

            TrainingAssignment assignment = TrainingAssignment.builder()
                    .userId(userId)
                    .trainingId(trainingId)
                    .progress(0)
                    .status("not-started")
                    .dueDate(updateRequest.getDueDate())
                    .assignedAt(Instant.now())
                    .build();

            assignmentRepo.save(assignment);
            newlyAssigned.add(userId);

            // Log training assigned
            auditLogService.log(
                    AuditAction.TRAINING_ASSIGNED,
                    "Training",
                    trainingId.toString(),
                    material.getTitle(),
                    "Training assigned to user: " + userId,
                    admin,
                    null,
                    request
            );
        }

        for (TrainingAssignment a : existing) {
            if (!newUserIds.contains(a.getUserId())) {
                assignmentRepo.delete(a);
            } else {
                a.setDueDate(updateRequest.getDueDate());
                assignmentRepo.save(a);
            }
        }

        material.setAssignedTo((int) assignmentRepo.countByTrainingId(trainingId));
        materialRepo.save(material);

        if (!newlyAssigned.isEmpty()) {
            fcmService.notifyTrainingAssigned(trainingId.toString(), newlyAssigned);
        }

        return material;
    }

    public void deleteTraining(Long trainingId, AuthenticatedUser admin) {

        TrainingMaterial material =
                materialRepo.findByIdAndActiveTrue(trainingId)
                        .orElseThrow(() -> new RuntimeException("Training not found"));

        // Log before deletion
        auditLogService.log(
                AuditAction.TRAINING_DELETED,
                "Training",
                material.getId().toString(),
                material.getTitle(),
                "Training deleted: " + material.getTitle(),
                admin,
                null,
                request
        );

        material.setActive(false);
        material.setDeletedAt(Instant.now());

        materialRepo.save(material);
    }
}