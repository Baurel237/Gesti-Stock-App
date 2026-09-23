package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStockResponse {

    private Long warehouseId;
    private String warehouseName;
    private Integer quantity;
}