package com.gis.servelq.controllers;

import com.gis.servelq.dto.BranchRequest;
import com.gis.servelq.dto.BranchResponse;
import com.gis.servelq.services.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    // Create a new branch
    @PostMapping
    public ResponseEntity<?> createBranch(@Valid @RequestBody BranchRequest branchRequest) {
        try {
            BranchResponse createdBranch = branchService.createBranch(branchRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdBranch);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get all branches
    @GetMapping
    public ResponseEntity<List<BranchResponse>> getAllBranches() {
        List<BranchResponse> branches = branchService.getAllBranches();
        return ResponseEntity.ok(branches);
    }

    // Get branch by ID
    @GetMapping("/{id}")
    public ResponseEntity<BranchResponse> getBranchById(@PathVariable String id) {
        Optional<BranchResponse> branch = branchService.getBranchById(id);
        return branch.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get branch by code
    @GetMapping("/code/{code}")
    public ResponseEntity<BranchResponse> getBranchByCode(@PathVariable String code) {
        Optional<BranchResponse> branch = branchService.getBranchByCode(code);
        return branch.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update branch
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBranch(@PathVariable String id, @Valid @RequestBody BranchRequest branchDetails) {
        try {
            BranchResponse updatedBranch = branchService.updateBranch(id, branchDetails);
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

    // Disable branch
    @PatchMapping("/{id}/disable")
    public ResponseEntity<?> disableBranch(@PathVariable String id) {
        try {
            BranchResponse disabledBranch = branchService.disableBranch(id);
            return ResponseEntity.ok(disabledBranch);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Enable branch
    @PatchMapping("/{id}/enable")
    public ResponseEntity<?> enableBranch(@PathVariable String id) {
        try {
            BranchResponse enabledBranch = branchService.enableBranch(id);
            return ResponseEntity.ok(enabledBranch);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}