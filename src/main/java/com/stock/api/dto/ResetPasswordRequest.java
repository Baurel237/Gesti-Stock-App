package com.stock.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requête de réinitialisation du mot de passe d'un utilisateur par
 * l'administration (PUT /api/admin/users/{id}/password).
 * newPassword est facultatif : null ou blanc → mot de passe temporaire
 * généré automatiquement par le service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {

    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String newPassword;
}
