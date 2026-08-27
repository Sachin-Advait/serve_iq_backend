package com.gis.servelq.repository;

import com.gis.servelq.models.TvContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TvContentRepository extends JpaRepository<TvContent, String> {

    // ==================== EXISTING METHODS ====================

    List<TvContent> findByBranchIdAndTypeIn(String branchId, List<String> types);

    List<TvContent> findByBranchIdAndTypeInAndArchivedFalse(String branchId, List<String> types);

    List<TvContent> findByBranchIdAndType(String branchId, String type);

    List<TvContent> findByBranchIdAndTypeAndActive(String branchId, String type, Boolean active);

    List<TvContent> findByBranchIdAndTypeOrderByCreatedAtDesc(String branchId, String type);

    List<TvContent> findByBranchIdAndArchivedTrue(String branchId);

    List<TvContent> findByBranchIdAndArchivedFalse(String branchId);

    // ==================== NEW METHODS ====================

    // Get all active and non-archived content ordered by creation date
    List<TvContent> findByActiveTrueAndArchivedFalseOrderByCreatedAtDesc();

    // Get active content by type (IMAGE, VIDEO) ordered by creation date
    List<TvContent> findByTypeAndActiveTrueAndArchivedFalseOrderByCreatedAtDesc(String type);

    // NEW: Get content by multiple types (VIDEO, IPTV_URL)
    List<TvContent> findByTypeInAndActiveTrueAndArchivedFalseOrderByCreatedAtDesc(List<String> types);

    // Get active content by type
    List<TvContent> findByTypeAndActiveTrue(String type);

    // Get all active content
    List<TvContent> findByActiveTrue();

    // Get archived content
    List<TvContent> findByArchivedTrue();
}