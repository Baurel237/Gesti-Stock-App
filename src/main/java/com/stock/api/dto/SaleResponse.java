package com.stock.api.dto;

import com.stock.api.entity.Sale.PaymentMethod;
import com.stock.api.entity.Sale.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponse {
    private Long id;
    private String reference;
    private SaleStatus status;
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private List<SaleItemResponse> items;
    private Long sellerId;
    private String sellerEmail;
    private String sellerName;
    private String buyerName;
    private String buyerPhone;
    private String notes;
    /** Demande d'annulation en attente (émise par le vendeur après 5 min). */
    private boolean cancellationRequested;
    private String cancellationReason;
    private LocalDateTime cancellationRequestedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
