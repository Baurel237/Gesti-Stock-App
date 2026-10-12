package com.stock.api.dto;

import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.entity.Order.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private String reference;
    private OrderType type;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderLineResponse> lines;
    private Long createdById;
    private String createdByEmail;
    private String createdByName;
    private Long supplierId;
    private String supplierName;
    private String clientName;
    private LocalDate deliveryDate;

    /** Entrepôt / boutique d'origine (V11) — servi sur le ticket. */
    private Long warehouseId;
    private String warehouseName;
    private String warehouseLocation;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
