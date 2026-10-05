package com.stock.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    /** RG-05 : au moins un rôle doit être assigné. */
    private Set<String> roles;

    private Boolean active;

    /**
     * Entrepôts affectés (story 1.1) — IDs d'entrepôts de l'entreprise.
     * null = inchangé ; liste vide = retrait de toutes les affectations
     * (l'utilisateur redevient sans restriction).
     */
    private Set<Long> warehouseIds;
}
