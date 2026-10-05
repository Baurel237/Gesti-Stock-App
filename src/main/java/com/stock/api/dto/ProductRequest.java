package com.stock.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ProductRequest {

    @NotBlank(message = "Le nom du produit est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;

    @NotBlank(message = "La référence est obligatoire")
    @Size(max = 50, message = "La référence ne doit pas dépasser 50 caractères")
    private String reference;

    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "0.01", message = "Le prix doit être supérieur à 0")
    private BigDecimal price;

    @NotNull(message = "L'ID de la catégorie est obligatoire")
    private Long categoryId;

    @Min(value = 0, message = "La quantité ne peut pas être négative")
    private Integer quantity;

    @Min(value = 0, message = "Le seuil d'alerte ne peut pas être négatif")
    private Integer alertThreshold;

    /**
     * Fournisseurs du produit (OPTIONNEL, story 2.3) — ids de fournisseurs
     * actifs de la société du produit. null = inchangé en mise à jour ;
     * liste vide = retirer tous les fournisseurs.
     */
    private List<Long> supplierIds;
}
