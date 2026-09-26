package com.stock.api.repository;

import com.stock.api.entity.Order;
import com.stock.api.entity.Order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findByCreatedByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByReference(String reference);

    @Query("SELECT o FROM Order o WHERE " +
            "(:companyId IS NULL OR o.companyId = :companyId) " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:createdById IS NULL OR o.createdBy.id = :createdById) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findByFilters(@Param("companyId") Long companyId,
                               @Param("status") OrderStatus status,
                               @Param("createdById") Long createdById,
                               Pageable pageable);

    /** Agrégats dashboard : COUNT des commandes en attente. */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'PENDING' " +
           "AND (:companyId IS NULL OR o.companyId = :companyId)")
    long countPendingOrders(@Param("companyId") Long companyId);
}
