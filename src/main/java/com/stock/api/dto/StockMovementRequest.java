package com.stock.api.dto;

import com.stock.api.entity.StockMovement.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementRequest {

    /**
     * Entrepôt concerné (OPTIONNEL — module entrepôts activé). Null en mode
     * stock simple : l'opération touche uniquement la quantité globale produit.
     */
    private Long warehouseId;

    @NotNull(message = "Le type de mouvement est obligatoire")
    private MovementType type;

    @NotNull(message = "L'ID du produit est obligatoire")
    private Long productId;

    @NotNull(message = "La quantité est obligatoire")
    @Min(value = 1, message = "La quantité doit être au moins 1")
    private Integer quantity;

    private String reason;
}
