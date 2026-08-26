package com.gis.servelq.repository;

import com.gis.servelq.models.BreakingNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BreakingNewsRepository extends JpaRepository<BreakingNews, String> {

    // ==================== BREAKING NEWS QUERIES ====================

    // Get all active breaking news (paginated)
    Page<BreakingNews> findByPublishedTrueAndActiveTrueAndArchivedFalse(Pageable pageable);

    // Get active breaking news by type (paginated)
    Page<BreakingNews> findByPublishedTrueAndActiveTrueAndArchivedFalseAndType(Pageable pageable, String type);

    // Get active breaking news by title search (paginated)
    Page<BreakingNews> findByPublishedTrueAndActiveTrueAndArchivedFalseAndTitleContainingIgnoreCase(Pageable pageable, String title);

    // Get active breaking news by type AND title search (paginated)
    Page<BreakingNews> findByPublishedTrueAndActiveTrueAndArchivedFalseAndTypeAndTitleContainingIgnoreCase(
            Pageable pageable, String type, String title);

    // Get active breaking news list (limited)
    List<BreakingNews> findByPublishedTrueAndActiveTrueAndArchivedFalseOrderByPublishedDateDesc(Pageable pageable);

    // ==================== ARCHIVED NEWS QUERIES ====================

    // Get all archived news (paginated)
    Page<BreakingNews> findByArchivedTrue(Pageable pageable);

    // Get archived news by title search (paginated)
    Page<BreakingNews> findByArchivedTrueAndTitleContainingIgnoreCase(Pageable pageable, String title);

    // Get archived news by type (paginated)
    Page<BreakingNews> findByArchivedTrueAndType(Pageable pageable, String type);

    // Get archived news by type AND title search (paginated)
    Page<BreakingNews> findByArchivedTrueAndTypeAndTitleContainingIgnoreCase(
            Pageable pageable, String type, String title);

    // ==================== OTHER QUERIES ====================

    // Get all published and non-archived news (for archiving)
    List<BreakingNews> findByPublishedTrueAndArchivedFalse();

    // Get all unpublished news
    List<BreakingNews> findByPublishedFalse();

    // Get the latest news item (for separator image) - NO Pageable parameter
    Optional<BreakingNews> findTopByOrderByPublishedDateDesc();

    // Alternative if you need pagination with findTop
    // List<BreakingNews> findTop1ByOrderByPublishedDateDesc();
}