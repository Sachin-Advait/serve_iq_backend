package com.gis.servelq.repository;

import com.gis.servelq.models.SmsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmsSettingsRepository extends JpaRepository<SmsSettings, Integer> {
}
