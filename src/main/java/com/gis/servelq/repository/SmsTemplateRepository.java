package com.gis.servelq.repository;

import com.gis.servelq.models.SmsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, String> {
    Optional<SmsTemplate> findByCode(String code);

    List<SmsTemplate> findAllByOrderByNameAsc();
}
