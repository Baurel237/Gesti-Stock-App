package com.stock.api.service;

import com.stock.api.entity.User;
import com.stock.api.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Petit helper partagé entre services : résout l'utilisateur authentifié
 * courant à partir du contexte de sécurité (null si non résolvable —
 * seed, tâche système...).
 */
public final class CurrentUserHolder {

    private CurrentUserHolder() {
    }

    public static User currentUser(UserRepository userRepository) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
                return null;
            }
            return userRepository.findByEmail(auth.getName()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
