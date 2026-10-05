package com.stock.api.repository;

import com.stock.api.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Liste paginée avec recherche facultative par nom (contient, insensible
     * à la casse) — isolation par entreprise : companyId null = portée
     * plateforme (SUPER_ADMIN voit tout).
     */
    @Query("SELECT s FROM Supplier s WHERE s.deleted = false " +
           "AND (:companyId IS NULL OR s.companyId = :companyId) " +
           "AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Supplier> findByFilters(@Param("companyId") Long companyId,
                                 @Param("search") String search,
                                 Pageable pageable);

    Optional<Supplier> findByIdAndDeletedFalse(Long id);

    boolean existsByCompanyIdAndNameAndDeletedFalse(Long companyId, String name);
}
