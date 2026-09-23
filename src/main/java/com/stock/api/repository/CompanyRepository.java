package com.stock.api.repository;

import com.stock.api.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<Company> findFirstByOrderByIdAsc();

    Optional<Company> findFirstByActiveTrueOrderByIdAsc();
}
