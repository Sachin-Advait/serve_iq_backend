package com.gis.servelq.repository;

import com.gis.servelq.models.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
    Optional<AppConfig> findByAppType(String appType);
    boolean existsByAppType(String appType);
}