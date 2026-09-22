package com.stock.api.repository;

import com.stock.api.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByDeletedFalse(Pageable pageable);

    Page<Product> findByCategory_IdAndDeletedFalse(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    Page<Product> findByQuantityLessThanAndDeletedFalse(Integer threshold, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.quantity <= p.alertThreshold")
    Page<Product> findLowStockProducts(Pageable pageable);

    @Query(value = "SELECT * FROM products p WHERE p.deleted = false " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE '%' || LOWER(CAST(:name AS text)) || '%')",
            countQuery = "SELECT COUNT(*) FROM products p WHERE p.deleted = false " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE '%' || LOWER(CAST(:name AS text)) || '%')",
            nativeQuery = true)
    Page<Product> findByFilters(@Param("categoryId") Long categoryId,
                                @Param("name") String name,
                                Pageable pageable);

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Optional<Product> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    boolean existsByReferenceAndDeletedFalse(String reference);
}
