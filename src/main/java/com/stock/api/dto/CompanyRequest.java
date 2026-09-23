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
public class CompanyRequest {

    /** Nom de l'entreprise. */
    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    /** Identifiant URL unique ; généré depuis le nom si absent. */
    @Size(max = 100, message = "L'identifiant ne doit pas dépasser 100 caractères")
    private String slug;

    /** Active le module entrepôts optionnel (défaut : false). */
    private Boolean warehouseEnabled;
}