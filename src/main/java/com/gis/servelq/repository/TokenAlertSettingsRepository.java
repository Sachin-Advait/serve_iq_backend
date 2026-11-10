package com.gis.servelq.repository;

import com.gis.servelq.models.TokenAlertSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenAlertSettingsRepository extends JpaRepository<TokenAlertSettings, Integer> {
}
