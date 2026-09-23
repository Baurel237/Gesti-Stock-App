package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Agrégats du tableau de bord — chaque métrique est un agrégat SQL
 * (une requête) au lieu d'une pagination complète côté client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    private long totalProducts;

    private long totalCategories;

    private long pendingOrders;

    private long lowStockProducts;

    private BigDecimal totalStockValue;
}
