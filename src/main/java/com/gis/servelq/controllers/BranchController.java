package com.gis.servelq.controllers;

import com.gis.servelq.dto.BranchRequest;
import com.gis.servelq.dto.BranchResponseDTO;
import com.gis.servelq.dto.BranchUpdateRequest;
import com.gis.servelq.services.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/serveiq/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    // Create a new branch
    @PostMapping
    public ResponseEntity<?> createBranch(@Valid @RequestBody BranchRequest branchRequest) {
        try {
            BranchResponseDTO createdBranch = branchService.createBranch(branchRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdBranch);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get all branches
    @GetMapping
    public ResponseEntity<List<BranchResponseDTO>> getAllBranches() {
        List<BranchResponseDTO> branches = branchService.getAllBranches();
        return ResponseEntity.ok(branches);
    }

    // Get branch by ID
    @GetMapping("/{id}")
    public ResponseEntity<BranchResponseDTO> getBranchById(@PathVariable String id) {
        Optional<BranchResponseDTO> branch = branchService.getBranchById(id);
        return branch.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get branch by code
    @GetMapping("/code/{code}")
    public ResponseEntity<BranchResponseDTO> getBranchByCode(@PathVariable String code) {
        Optional<BranchResponseDTO> branch = branchService.getBranchByCode(code);
        return branch.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update branch
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBranch(@PathVariable String id, @RequestBody BranchUpdateRequest branchDetails) {
        try {
            BranchResponseDTO updatedBranch = branchService.updateBranch(id, branchDetails);
            return ResponseEntity.ok(updatedBranch);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Delete branch
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBranch(@PathVariable String id) {
        try {
            branchService.deleteBranch(id);
            return ResponseEntity.ok().body("Branch deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}