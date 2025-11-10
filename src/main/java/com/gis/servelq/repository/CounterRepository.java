package com.gis.servelq.repository;

import com.gis.servelq.models.Counter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterRepository extends JpaRepository<Counter, String> {
    List<Counter> findByBranchIdAndEnabledTrue(String branchId);
    Optional<Counter> findByCodeAndBranchId(String code, String branchId);
    List<Counter> findByBranchIdAndServicesIdAndEnabledTrue(String branchId, String serviceId);
}