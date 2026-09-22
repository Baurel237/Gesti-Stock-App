package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementTotalsResponse {
    private BigDecimal totalEntryAmount;
    private BigDecimal totalExitAmount;
    private long totalEntryCount;
    private long totalExitCount;
}
