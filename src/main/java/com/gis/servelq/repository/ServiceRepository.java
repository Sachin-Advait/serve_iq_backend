package com.gis.servelq.repository;

import com.gis.servelq.models.ServiceModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceModel, String> {
    List<ServiceModel> findByBranchIdAndEnabledTrue(String branchId);
    List<ServiceModel> findByParentIdAndEnabledTrue(String parentId);
    Optional<ServiceModel> findByCodeAndBranchId(String code, String branchId);

    @Query("SELECT s FROM ServiceModel s WHERE s.branchId = :branchId AND s.enabled = true " +
            "AND (s.parentId IS NULL OR s.parentId = '')")
    List<ServiceModel> findMainServicesByBranchId(@Param("branchId") String branchId);
}