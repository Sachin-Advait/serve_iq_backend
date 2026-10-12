package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.Exceptions.TicketIssuingClosedException;
import com.gis.servelq.dto.BranchRequest;
import com.gis.servelq.dto.BranchResponseDTO;
import com.gis.servelq.dto.BranchUpdateRequest;
import com.gis.servelq.dto.TicketIssuingRequest;
import com.gis.servelq.models.AuditAction;
import com.gis.servelq.models.Branch;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final AuditLogService auditLogService;
    private final HttpServletRequest request;
    private final BranchRepository branchRepository;

    private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");

    // Convert Entity to Response DTO
    private BranchResponseDTO convertToResponse(Branch branch) {
        BranchResponseDTO response = new BranchResponseDTO();
        response.setId(branch.getId());
        response.setCode(branch.getCode());
        response.setName(branch.getName());
        response.setEnabled(branch.getEnabled());
        response.setCreatedAt(branch.getCreatedAt());
        response.setUpdatedAt(branch.getUpdatedAt());
        response.setTicketIssuingEnabled(branch.getTicketIssuingEnabled());
        response.setTicketIssuingStart(branch.getTicketIssuingStart());
        response.setTicketIssuingEnd(branch.getTicketIssuingEnd());
        response.setOfficeCloseTime(branch.getOfficeCloseTime());
        response.setTicketStopMessageEn(branch.getTicketStopMessageEn());
        response.setTicketStopMessageAr(branch.getTicketStopMessageAr());
        return response;
    }

    // Convert Request DTO to Entity
    private Branch convertToEntity(BranchRequest request) {
        Branch branch = new Branch();
        branch.setCode(request.getCode());
        branch.setName(request.getName());
        branch.setEnabled(request.getEnabled());
        return branch;
    }

    // Create a new branch
    @Transactional
    public BranchResponseDTO createBranch(BranchRequest branchRequest, AuthenticatedUser user) {
        // Check if branch code already exists
        if (branchRepository.findByCode(branchRequest.getCode()).isPresent()) {
            throw new RuntimeException("Branch with code " + branchRequest.getCode() + " already exists");
        }

        Branch branch = convertToEntity(branchRequest);

        // Set default enabled value if null
        if (branch.getEnabled() == null) {
            branch.setEnabled(true);
        }

        Branch savedBranch = branchRepository.save(branch);
        auditLogService.log(
                AuditAction.BRANCH_CREATED,
                "Branch",
                savedBranch.getId(),
                savedBranch.getName(),
                "Branch created: " + savedBranch.getName() + " (" + savedBranch.getCode() + ")",
                user,
                savedBranch.getId(),
                request
        );
        return convertToResponse(savedBranch);
    }

    // Get all branches
    public List<BranchResponseDTO> getAllBranches() {
        return branchRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // Get branch by ID
    public Optional<BranchResponseDTO> getBranchById(String id) {
        return branchRepository.findById(id)
                .map(this::convertToResponse);
    }

    // Get branch by code
    public Optional<BranchResponseDTO> getBranchByCode(String code) {
        return branchRepository.findByCode(code)
                .map(this::convertToResponse);
    }

    @Transactional
    public BranchResponseDTO updateBranch(String id, BranchUpdateRequest branchDetails , AuthenticatedUser user) {
        Branch existingBranch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + id));

        if (!existingBranch.getCode().equals(branchDetails.getCode()) &&
                branchRepository.findByCode(branchDetails.getCode()).isPresent()) {
            throw new RuntimeException("Branch with code " + branchDetails.getCode() + " already exists");
        }

        if (branchDetails.getCode() != null) existingBranch.setCode(branchDetails.getCode());
        if (branchDetails.getName() != null) existingBranch.setName(branchDetails.getName());
        if (branchDetails.getEnabled() != null) existingBranch.setEnabled(branchDetails.getEnabled());

        Branch updatedBranch = branchRepository.save(existingBranch);

        auditLogService.logWithChanges(
                AuditAction.BRANCH_UPDATED,
                "Branch",
                updatedBranch.getId(),
                updatedBranch.getName(),
                "Branch updated: " + updatedBranch.getName(),
                existingBranch,
                updatedBranch,
                user,
                updatedBranch.getId(),
                request
        );
        return convertToResponse(updatedBranch);
    }

    /**
     * Stop or resume ticket issuing and set the issuing hours (procedure 10).
     * Only ADMIN and MANAGER reach /branches/**.
     */
    @Transactional
    public BranchResponseDTO updateTicketIssuing(String id, TicketIssuingRequest body, AuthenticatedUser user) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));

        if ((body.getStart() == null) != (body.getEnd() == null)) {
            throw new BusinessException("Set both the start and end of the issuing hours, or neither");
        }
        if (body.getStart() != null && !body.getStart().isBefore(body.getEnd())) {
            throw new BusinessException("Issuing hours must start before they end");
        }

        boolean wasEnabled = !Boolean.FALSE.equals(branch.getTicketIssuingEnabled());
        branch.setTicketIssuingEnabled(body.getEnabled() == null || body.getEnabled());
        branch.setTicketIssuingStart(body.getStart());
        branch.setTicketIssuingEnd(body.getEnd());
        branch.setOfficeCloseTime(body.getOfficeCloseTime());
        branch.setTicketStopMessageEn(blankToNull(body.getMessageEn()));
        branch.setTicketStopMessageAr(blankToNull(body.getMessageAr()));
        Branch saved = branchRepository.save(branch);

        String state = saved.getTicketIssuingEnabled()
                ? (wasEnabled ? "Ticket issuing settings updated" : "Ticket issuing resumed")
                : "Ticket issuing stopped";
        String hours = saved.getTicketIssuingStart() != null
                ? " (hours " + saved.getTicketIssuingStart() + "-" + saved.getTicketIssuingEnd() + ")"
                : "";
        String close = saved.getOfficeCloseTime() != null
                ? ", office closes " + saved.getOfficeCloseTime()
                : "";
        auditLogService.log(
                AuditAction.TICKET_ISSUING_CHANGED,
                "Branch",
                saved.getId(),
                saved.getName(),
                state + hours + close,
                user,
                saved.getId(),
                request
        );
        return convertToResponse(saved);
    }

    /**
     * Throws when the branch is not issuing tickets right now. The message is
     * shown on the kiosk, so it follows the kiosk's language ("ar" or English).
     */
    public void assertTicketIssuingOpen(String branchId, String language) {
        if (branchId == null) return;
        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null) return;

        boolean arabic = "ar".equalsIgnoreCase(language) || "arabic".equalsIgnoreCase(language);

        if (Boolean.FALSE.equals(branch.getTicketIssuingEnabled())) {
            String custom = arabic ? branch.getTicketStopMessageAr() : branch.getTicketStopMessageEn();
            throw new TicketIssuingClosedException(custom != null ? custom : arabic
                    ? "توقف إصدار التذاكر حالياً. يرجى استخدام الخدمات الإلكترونية أو الاتصال بمركز الاتصال."
                    : "Ticket issuing has stopped for now. Please use our online services or call the Contact Centre.");
        }

        LocalTime start = branch.getTicketIssuingStart();
        LocalTime end = branch.getTicketIssuingEnd();
        if (start != null && end != null) {
            LocalTime now = LocalTime.now();
            if (now.isBefore(start) || !now.isBefore(end)) {
                String from = start.format(HOURS);
                String to = end.format(HOURS);
                throw new TicketIssuingClosedException(arabic
                        ? "يتم إصدار التذاكر من " + from + " إلى " + to + "."
                        : "Tickets are issued from " + from + " to " + to + ".");
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Delete branch
    @Transactional
    public void deleteBranch(String id, AuthenticatedUser user) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + id));
        auditLogService.log(
                AuditAction.BRANCH_DELETED,
                "Branch",
                branch.getId(),
                branch.getName(),
                "Branch deleted: " + branch.getName(),
                user,
                branch.getId(),
                request
        );
        branchRepository.deleteById(id);
    }
}