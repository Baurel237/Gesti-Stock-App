package com.stock.api.repository;

import com.stock.api.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByDeletedFalse(Pageable pageable);

    Page<Product> findByCategory_IdAndDeletedFalse(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);

    Page<Product> findByQuantityLessThanAndDeletedFalse(Integer threshold, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.quantity <= p.alertThreshold " +
           "AND (:companyId IS NULL OR p.companyId = :companyId)")
    Page<Product> findLowStockProducts(@Param("companyId") Long companyId, Pageable pageable);

    @Query(value = "SELECT * FROM products p WHERE p.deleted = false " +
            "AND (:companyId IS NULL OR p.company_id = :companyId) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:supplierId IS NULL OR EXISTS (SELECT 1 FROM product_suppliers ps " +
            "     WHERE ps.product_id = p.id AND ps.supplier_id = :supplierId)) " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE '%' || LOWER(CAST(:name AS text)) || '%')",
            countQuery = "SELECT COUNT(*) FROM products p WHERE p.deleted = false " +
            "AND (:companyId IS NULL OR p.company_id = :companyId) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:supplierId IS NULL OR EXISTS (SELECT 1 FROM product_suppliers ps " +
            "     WHERE ps.product_id = p.id AND ps.supplier_id = :supplierId)) " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE '%' || LOWER(CAST(:name AS text)) || '%')",
            nativeQuery = true)
    Page<Product> findByFilters(@Param("companyId") Long companyId,
                                @Param("categoryId") Long categoryId,
                                @Param("supplierId") Long supplierId,
                                @Param("name") String name,
                                Pageable pageable);

    /**
     * Catalogue d'une boutique (1 entrepôt = 1 boutique) : mêmes filtres que
     * findByFilters, restreint aux produits possédant du stock dans l'entrepôt
     * donné (product_stock_per_warehouse > 0). warehouseId null = pas de
     * restriction (vue Direction Générale / module désactivé).
     */
    @Query(value = "SELECT * FROM products p WHERE p.deleted = false " +
            "AND (:companyId IS NULL OR p.company_id = :companyId) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:supplierId IS NULL OR EXISTS (SELECT 1 FROM product_suppliers ps " +
            "     WHERE ps.product_id = p.id AND ps.supplier_id = :supplierId)) " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE '%' || LOWER(CAST(:name AS text)) || '%') " +
            "AND (:warehouseId IS NULL OR EXISTS (SELECT 1 FROM product_stock_per_warehouse ws " +
            "     WHERE ws.product_id = p.id AND ws.warehouse_id = :warehouseId " +
            "     AND ws.quantity > 0))",
            countQuery = "SELECT COUNT(*) FROM products p WHERE p.deleted = false " +
            "AND (:companyId IS NULL OR p.company_id = :companyId) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:supplierId IS NULL OR EXISTS (SELECT 1 FROM product_suppliers ps " +
            "     WHERE ps.product_id = p.id AND ps.supplier_id = :supplierId)) " +
            "AND (:name IS NULL OR LOWER(p.name) LIKE '%' || LOWER(CAST(:name AS text)) || '%') " +
            "AND (:warehouseId IS NULL OR EXISTS (SELECT 1 FROM product_stock_per_warehouse ws " +
            "     WHERE ws.product_id = p.id AND ws.warehouse_id = :warehouseId " +
            "     AND ws.quantity > 0))",
            nativeQuery = true)
    Page<Product> findByFiltersForWarehouse(@Param("companyId") Long companyId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("supplierId") Long supplierId,
                                            @Param("name") String name,
                                            @Param("warehouseId") Long warehouseId,
                                            Pageable pageable);

    /**
     * Stories 2.3/2.4 : dé-lie un fournisseur (supprimé logiquement) de tous
     * les produits — ses produits redeviennent visibles sous « Tous les
     * fournisseurs » et n'apparaissent plus dans les sélecteurs.
     */
    @Modifying
    @Query(value = "DELETE FROM product_suppliers WHERE supplier_id = :supplierId", nativeQuery = true)
    void unlinkSupplier(@Param("supplierId") Long supplierId);

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Optional<Product> findByNameAndDeletedFalse(String name);

    boolean existsByNameAndDeletedFalse(String name);

    boolean existsByReferenceAndDeletedFalse(String reference);

    /** Agrégats dashboard : un COUNT et une somme SQL au lieu de paginer tous les produits. */
    @Query("SELECT COUNT(p), COALESCE(SUM(p.price * p.quantity), 0) FROM Product p " +
           "WHERE p.deleted = false " +
           "AND (:companyId IS NULL OR p.companyId = :companyId)")
    List<Object[]> getDashboardAggregates(@Param("companyId") Long companyId);

    /** Produits en stock bas : COUNT seul (le contenu paginé a sa propre requête). */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.deleted = false " +
           "AND p.quantity <= p.alertThreshold " +
           "AND (:companyId IS NULL OR p.companyId = :companyId)")
    long countLowStockProducts(@Param("companyId") Long companyId);
}
