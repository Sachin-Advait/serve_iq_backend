package com.gis.servelq.repository;

import com.gis.servelq.models.AppConfigTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppConfigTemplateRepository extends JpaRepository<AppConfigTemplate, String> {
    List<AppConfigTemplate> findByAppTypeOrderByCreatedAtDesc(String appType);
    Optional<AppConfigTemplate> findByAppTypeAndIsActiveTrue(String appType);
    // The TV has two default templates (Arabic Dark first, Arabic Light); the
    // oldest one is the fallback when no template is active.
    Optional<AppConfigTemplate> findFirstByAppTypeAndIsDefaultTrueOrderByCreatedAtAsc(String appType);
    List<AppConfigTemplate> findByAppTypeAndIsDefaultTrueOrderByCreatedAtAsc(String appType);
    long countByAppTypeAndIsActiveTrue(String appType);
}