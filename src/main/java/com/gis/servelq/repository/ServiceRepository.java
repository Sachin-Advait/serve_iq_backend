package com.gis.servelq.repository;

import com.gis.servelq.models.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Services, String> {

    List<Services> findByBranchId(String branchId);

    // Consumer/kiosk — enabled only (UNCHANGED)
    List<Services> findByParentIdAndEnabledTrue(String parentId);

    // Admin — all sub-services regardless of enabled
    List<Services> findByParentId(String parentId);

    Optional<Services> findByCodeAndBranchId(String code, String branchId);

    // Consumer/kiosk — enabled only main services (UNCHANGED)
    @Query("""
                SELECT s FROM Services s
                WHERE s.branchId = :branchId
                  AND s.enabled = true
                  AND (s.parentId IS NULL OR s.parentId = '')
            """)
    List<Services> findMainServicesByBranchId(@Param("branchId") String branchId);

    // Admin — all main services regardless of enabled
    @Query("""
                SELECT s FROM Services s
                WHERE s.branchId = :branchId
                  AND (s.parentId IS NULL OR s.parentId = '')
            """)
    List<Services> findAllMainServicesByBranchId(@Param("branchId") String branchId);
}