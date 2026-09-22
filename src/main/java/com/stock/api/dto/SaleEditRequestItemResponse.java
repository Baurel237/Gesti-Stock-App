package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Article demandé dans une demande de modification de vente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleEditRequestItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productReference;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}