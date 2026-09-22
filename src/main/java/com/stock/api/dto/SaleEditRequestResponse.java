package com.stock.api.dto;

import com.stock.api.entity.Sale.PaymentMethod;
import com.stock.api.entity.SaleEditRequest.SaleEditRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demande de modification d'une vente (vue admin).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleEditRequestResponse {
    private Long id;
    private Long saleId;
    private String saleReference;
    private SaleEditRequestStatus status;
    private String reason;
    private PaymentMethod paymentMethod;
    private String buyerName;
    private String buyerPhone;
    private String notes;
    private List<SaleEditRequestItemResponse> items;
    private BigDecimal totalAmount;
    private Long requestedById;
    private String requestedByName;
    private LocalDateTime requestedAt;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
}