package com.stock.api.dto;

import com.stock.api.entity.StockMovement.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {

    private Long id;
    private MovementType type;
    private Long productId;
    private String productName;
    private Integer quantity;
    private String reason;
    private Long performedById;
    private String performedByEmail;
    private Long orderId;

    /** Fournisseur du réapprovisionnement (story 2.2) — null si absent. */
    private Long supplierId;
    private String supplierName;

    private LocalDateTime createdAt;
}
