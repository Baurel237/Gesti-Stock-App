package com.stock.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseRequest {

    @NotBlank(message = "Le nom de l'entrepôt est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    @Size(max = 200, message = "L'adresse ne doit pas dépasser 200 caractères")
    private String location;

    /** Disabled = conservé pour l'historique (défaut : true à la création). */
    private Boolean active;
}