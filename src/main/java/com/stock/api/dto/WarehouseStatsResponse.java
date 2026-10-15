package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Statistiques d'une boutique (entrepôt) pour son propre tableau de bord :
 * produits disponibles, valeur du stock local, ventes et mouvements de
 * l'entrepôt uniquement. La Direction Générale consulte ces stats
 * entrepôt par entrepôt, plus son dashboard global consolidé.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseStatsResponse {

    /** Entrepôt concerné. */
    private Long warehouseId;
    private String warehouseName;

    /** Produits différents disponibles (stock > 0) dans la boutique. */
    private long availableProducts;

    /** Valeur du stock local (somme quantité × prix unitaire). */
    private BigDecimal stockValue;

    /** Total des unités en stock dans la boutique. */
    private long totalUnits;

    /** Nombre de ventes rattachées à la boutique. */
    private long salesCount;

    /** Chiffre d'affaires cumulé de la boutique (somme des ventes). */
    private BigDecimal salesTotalAmount;

    /** Mouvements de stock (entrées + sorties) de la boutique. */
    private long movementsCount;
}
