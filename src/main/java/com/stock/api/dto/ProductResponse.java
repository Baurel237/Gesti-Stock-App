package com.stock.api.dto;

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
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private String reference;
    private String imageUrl;
    private BigDecimal price;
    private Integer quantity;
    private Integer alertThreshold;
    private boolean lowStock;
    private Long categoryId;
    private String categoryName;

    /** Fournisseurs du produit (stories 2.3/2.4) — vide si aucun. */
    private List<Long> supplierIds;

    /** Noms des fournisseurs, dans l'ordre de rattachement (affichage détail). */
    private List<String> supplierNames;

    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
