package com.gis.servelq.repository;

import com.gis.servelq.models.TransferReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferReasonRepository extends JpaRepository<TransferReason, String> {
    List<TransferReason> findAllByOrderBySortOrderAscTitleEnAsc();

    List<TransferReason> findByActiveTrueOrderBySortOrderAscTitleEnAsc();

    Optional<TransferReason> findByCode(String code);

    boolean existsByCode(String code);
}
