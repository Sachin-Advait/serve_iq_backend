package com.gis.servelq.repository;

import com.gis.servelq.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findByPriority(Integer priority);

    boolean existsByCode(String code);

    // code is expected upper case and trimmed (CodeUniquenessValidator.normalize)
    @Query("SELECT c FROM Category c WHERE UPPER(TRIM(c.code)) = :code")
    List<Category> findByNormalizedCode(@Param("code") String code);
}