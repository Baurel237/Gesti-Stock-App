package com.stock.api.repository;

import com.stock.api.entity.Sale;
import com.stock.api.entity.Sale.SaleStatus;
import com.stock.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Page<Sale> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    Page<Sale> findBySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, SaleStatus status, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.seller.id = :sellerId AND s.status = 'COMPLETED' AND s.createdAt >= :start AND s.createdAt < :end")
    Integer countBySellerIdAndDateRange(@Param("sellerId") Long sellerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.seller.id = :sellerId AND s.status = 'COMPLETED' AND s.createdAt >= :start AND s.createdAt < :end")
    BigDecimal sumTotalBySellerIdAndDateRange(@Param("sellerId") Long sellerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Sale> findTop10BySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, SaleStatus status);

    /** Flux temps réel : dernières ventes COMPLETED, toutes vendeuses confondues. */
    List<Sale> findTopNByStatusOrderByCreatedAtDesc(SaleStatus status, Pageable pageable);

    long countBySellerId(Long sellerId);

    /** Demandes d'annulation en attente (traitées par l'administration). */
    List<Sale> findByCancellationRequestedTrueOrderByCancellationRequestedAtAsc();

    @Query("SELECT s.seller.id, s.seller.email, s.seller.firstName, s.seller.lastName, " +
           "COALESCE(SUM(s.totalItems), 0), COALESCE(SUM(s.totalAmount), 0), COUNT(s) " +
           "FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt >= :start AND s.createdAt < :end " +
           "GROUP BY s.seller.id, s.seller.email, s.seller.firstName, s.seller.lastName")
    List<Object[]> findSellerStatsByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', s.createdAt), COUNT(s), COALESCE(SUM(s.totalAmount), 0) " +
           "FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt >= :start AND s.createdAt < :end " +
           "GROUP BY FUNCTION('DATE', s.createdAt) ORDER BY FUNCTION('DATE', s.createdAt)")
    List<Object[]> findDailySalesAggregation(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Re-dater une vente (réservé au seed de démonstration) : createdAt est
     * otherwise immuable (updatable = false).
     */
    @Modifying
    @Query("UPDATE Sale s SET s.createdAt = :createdAt, s.updatedAt = :createdAt WHERE s.id = :id")
    void backdateSale(@Param("id") Long id, @Param("createdAt") LocalDateTime createdAt);

    /** Purge des ventes d'un vendeur (suppression de son compte) ; lignes et
     * demandes liées supprimées via cascade/orphanRemoval. */
    void deleteBySeller(User seller);
}
