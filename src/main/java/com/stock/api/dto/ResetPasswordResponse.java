package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse de réinitialisation de mot de passe : le mot de passe temporaire
 * (généré ou saisi par l'admin) est retourné en clair une seule fois afin que
 * l'administrateur puisse le communiquer à l'utilisateur.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordResponse {

    /** Email du compte concerné. */
    private String email;

    /** Nom complet de l'utilisateur. */
    private String fullName;

    /** Mot de passe temporaire — affiché une seule fois. */
    private String temporaryPassword;

    /** true si le mot de passe a été généré automatiquement. */
    private boolean generated;
}
