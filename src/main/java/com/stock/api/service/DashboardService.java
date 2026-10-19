package com.stock.api.service;

import com.stock.api.dto.DashboardStatsResponse;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrégats du tableau de bord (US-06).
 * Chaque métrique est un agrégat SQL exécuté côté base — remplace la
 * pagination complète des produits côté client (N+1 requêtes HTTP).
 * Portée multi-entreprises : companyId du token, null = plateforme.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;

    /**
     * Agrégats du dashboard en 3 requêtes SQL (produits+valeur, catégories,
     * commandes en attente) — la valeur du stock est un SUM en base.
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        Long companyId = TenantContext.getCompanyId();

        List<Object[]> productAggregates = productRepository.getDashboardAggregates(companyId);
        Object[] row = productAggregates.isEmpty()
                ? new Object[]{0L, BigDecimal.ZERO}
                : productAggregates.get(0);

        long totalProducts = row[0] != null ? ((Number) row[0]).longValue() : 0;
        BigDecimal totalStockValue = row[1] != null
                ? new BigDecimal(row[1].toString())
                : BigDecimal.ZERO;

        return DashboardStatsResponse.builder()
                .totalProducts(totalProducts)
                .totalStockValue(totalStockValue)
                .totalCategories(categoryRepository.countByFilters(companyId))
                .pendingOrders(orderRepository.countPendingOrders(companyId))
                .lowStockProducts(productRepository.countLowStockProducts(companyId))
                .build();
    }
}
