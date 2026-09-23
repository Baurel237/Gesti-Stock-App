package com.stock.api.service;

import com.stock.api.dto.DashboardStatsResponse;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour DashboardService.
 * Les agrégats sont calculés en SQL (plus de pagination N+1 côté client)
 * et scopés par entreprise (companyId du token, null = plateforme).
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getStats() — Agrégats")
    class StatsTests {

        @Test
        @DisplayName("Retourne les agrégats complets pour un tenant scanné")
        void stats_companyScope() {
            TenantContext.setCompanyId(5L);
            when(productRepository.getDashboardAggregates(5L)).thenReturn(
                    List.<Object[]>of(new Object[]{42L, new BigDecimal("12345.50")}));
            when(categoryRepository.countByFilters(5L)).thenReturn(7L);
            when(orderRepository.countPendingOrders(5L)).thenReturn(3L);
            when(productRepository.countLowStockProducts(5L)).thenReturn(2L);

            DashboardStatsResponse stats = dashboardService.getStats();

            assertEquals(42, stats.getTotalProducts());
            assertEquals(new BigDecimal("12345.50"), stats.getTotalStockValue());
            assertEquals(7, stats.getTotalCategories());
            assertEquals(3, stats.getPendingOrders());
            assertEquals(2, stats.getLowStockProducts());
        }

        @Test
        @DisplayName("companyId null → portée plateforme (SUPER_ADMIN)")
        void stats_platformScope() {
            when(productRepository.getDashboardAggregates(null)).thenReturn(
                    List.<Object[]>of(new Object[]{10L, BigDecimal.ZERO}));
            when(categoryRepository.countByFilters(null)).thenReturn(1L);
            when(orderRepository.countPendingOrders(null)).thenReturn(0L);
            when(productRepository.countLowStockProducts(null)).thenReturn(0L);

            DashboardStatsResponse stats = dashboardService.getStats();

            assertEquals(10, stats.getTotalProducts());
            verify(productRepository).getDashboardAggregates(null);
        }

        @Test
        @DisplayName("Aucun produit → valeur de stock zéro, pas d'erreur")
        void stats_emptyDatabase() {
            when(productRepository.getDashboardAggregates(null)).thenReturn(List.of());
            when(categoryRepository.countByFilters(null)).thenReturn(0L);
            when(orderRepository.countPendingOrders(null)).thenReturn(0L);
            when(productRepository.countLowStockProducts(null)).thenReturn(0L);

            DashboardStatsResponse stats = dashboardService.getStats();

            assertEquals(0, stats.getTotalProducts());
            assertEquals(0, BigDecimal.ZERO.compareTo(stats.getTotalStockValue()));
        }

        @Test
        @DisplayName("Somme SQL null (table vide) → zéro")
        void stats_nullSumHandled() {
            when(productRepository.getDashboardAggregates(null)).thenReturn(
                    List.<Object[]>of(new Object[]{0L, null}));
            when(categoryRepository.countByFilters(null)).thenReturn(0L);
            when(orderRepository.countPendingOrders(null)).thenReturn(0L);
            when(productRepository.countLowStockProducts(null)).thenReturn(0L);

            DashboardStatsResponse stats = dashboardService.getStats();

            assertEquals(0, BigDecimal.ZERO.compareTo(stats.getTotalStockValue()));
        }
    }
}
