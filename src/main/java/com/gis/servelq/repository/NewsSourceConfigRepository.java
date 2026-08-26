package com.gis.servelq.repository;

import com.gis.servelq.models.NewsSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsSourceConfigRepository extends JpaRepository<NewsSourceConfig, String> {
    List<NewsSourceConfig> findByActiveTrue();
    Optional<NewsSourceConfig> findBySourceName(String sourceName);
}