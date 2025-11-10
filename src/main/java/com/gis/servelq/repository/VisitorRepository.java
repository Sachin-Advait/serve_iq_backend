package com.gis.servelq.repository;
import com.gis.servelq.models.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VisitorRepository extends JpaRepository<Visitor, String> {
    Optional<Visitor> findByCivilId(String civilId);
}