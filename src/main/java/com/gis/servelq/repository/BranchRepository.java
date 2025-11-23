package com.gis.servelq.repository;

import com.gis.servelq.models.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, String> {
    Optional<Branch> findByCode(String code);
    Optional<Branch> findByIdAndEnabledTrue(String id);

}