package com.gis.servelq.repository;

import com.gis.servelq.models.TvContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TvContentRepository extends JpaRepository<TvContent, String> {

    List<TvContent> findByBranchIdAndTypeIn(String branchId, List<String> types);

    List<TvContent> findByBranchIdAndTypeInAndArchivedFalse(String branchId, List<String> types);

    List<TvContent> findByBranchIdAndType(String branchId, String type);

    List<TvContent> findByBranchIdAndTypeAndActive(String branchId, String type, Boolean active);

    List<TvContent> findByBranchIdAndTypeOrderByCreatedAtDesc(String branchId, String type);

    List<TvContent> findByBranchIdAndArchivedTrue(String branchId);

    List<TvContent> findByBranchIdAndArchivedFalse(String branchId);
}