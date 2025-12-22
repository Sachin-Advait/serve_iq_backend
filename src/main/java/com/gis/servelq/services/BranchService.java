package com.gis.servelq.services;

import com.gis.servelq.dto.BranchRequest;
import com.gis.servelq.dto.BranchResponseDTO;
import com.gis.servelq.dto.BranchUpdateRequest;
import com.gis.servelq.models.Branch;
import com.gis.servelq.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    // Convert Entity to Response DTO
    private BranchResponseDTO convertToResponse(Branch branch) {
        BranchResponseDTO response = new BranchResponseDTO();
        response.setId(branch.getId());
        response.setCode(branch.getCode());
        response.setName(branch.getName());
        response.setEnabled(branch.getEnabled());
        response.setCreatedAt(branch.getCreatedAt());
        response.setUpdatedAt(branch.getUpdatedAt());
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
    public BranchResponseDTO createBranch(BranchRequest branchRequest) {
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
    public BranchResponseDTO updateBranch(String id, BranchUpdateRequest branchDetails) {
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
        return convertToResponse(updatedBranch);
    }

    // Delete branch
    @Transactional
    public void deleteBranch(String id) {
        if (!branchRepository.existsById(id)) {
            throw new RuntimeException("Branch not found with id: " + id);
        }
        branchRepository.deleteById(id);
    }
}