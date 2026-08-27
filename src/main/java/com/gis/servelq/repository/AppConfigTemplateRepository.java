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
    Optional<AppConfigTemplate> findByAppTypeAndIsDefaultTrue(String appType);
    long countByAppTypeAndIsActiveTrue(String appType);
}