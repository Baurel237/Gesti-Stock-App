package com.stock.api.repository;

import com.stock.api.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Page<Category> findByDeletedFalse(Pageable pageable);

    Optional<Category> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);
}
