package com.stock.api.repository;

import com.stock.api.entity.StockMovement;
import com.stock.api.entity.StockMovement.MovementType;
import com.stock.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    List<StockMovement> findByProductIdAndTypeOrderByCreatedAtDesc(Long productId, MovementType type);

    @Query(value = "SELECT sm.type as movement_type, SUM(sm.quantity * p.price) as total_amount, COUNT(*) as total_count " +
            "FROM stock_movements sm JOIN products p ON p.id = sm.product_id " +
            "GROUP BY sm.type", nativeQuery = true)
    List<Object[]> getTotalsByType();

    /** Purge des mouvements effectués par un utilisateur (suppression de son compte). */
    void deleteByPerformedBy(User performedBy);
}
