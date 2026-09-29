package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ligne du contenu d'un entrepôt : un produit et la quantité stockée
 * dans cet entrepôt. Sert à la page de détail d'un entrepôt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseContentLineResponse {

    private Long productId;
    private String productName;
    private String productReference;
    private BigDecimal unitPrice;
    private Integer quantity;
    /** Quantité × prix unitaire. */
    private BigDecimal lineValue;
}
