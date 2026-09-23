package com.stock.api.repository;

import com.stock.api.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Page<Category> findByDeletedFalse(Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.deleted = false " +
           "AND (:companyId IS NULL OR c.companyId = :companyId)")
    Page<Category> findByFilters(@Param("companyId") Long companyId, Pageable pageable);

    Optional<Category> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    boolean existsByCompanyIdAndNameAndDeletedFalse(Long companyId, String name);

    /** Agrégats dashboard : COUNT des catégories actives. */
    @Query("SELECT COUNT(c) FROM Category c WHERE c.deleted = false " +
           "AND (:companyId IS NULL OR c.companyId = :companyId)")
    long countByFilters(@Param("companyId") Long companyId);
}
