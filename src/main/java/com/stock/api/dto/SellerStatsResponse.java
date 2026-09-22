package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerStatsResponse {
    private Long sellerId;
    private String sellerEmail;
    private String sellerFirstName;
    private String sellerLastName;
    private Integer totalProductsSold;
    private BigDecimal totalSalesAmount;
    private Integer totalSalesCount;
}
