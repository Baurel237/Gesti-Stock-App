package com.stock.api.repository;

import com.stock.api.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findByCompanyIdOrderByCreatedAtAsc(Long companyId);

    List<Warehouse> findByCompanyIdAndActiveTrueOrderByCreatedAtAsc(Long companyId);

    Optional<Warehouse> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndNameAndActiveTrue(Long companyId, String name);

    long countByCompanyId(Long companyId);
}
