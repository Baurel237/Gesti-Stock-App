package com.stock.api.service;

import com.stock.api.entity.User;
import com.stock.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Préférences personnelles de l'utilisateur connecté.
 * La devise d'affichage est une préférence individuelle : chaque vendeur
 * peut choisir sa devise d'affichage (FCFA, EUR, USD...) indépendamment
 * de la devise globale, avec conversion selon les taux officiels.
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    /** Devises autorisées (ISO 4217) — doit rester synchronisé avec le front-end. */
    public static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("EUR", "USD", "XOF", "XAF", "GBP", "CHF", "CAD", "MAD", "NGN");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public String getCurrency() {
        return currentUser().getPreferredCurrency();
    }

    /**
     * Met à jour la devise d'affichage de l'utilisateur.
     * null = réinitialiser la préférence (retour à la devise globale).
     */
    @Transactional
    public String updateCurrency(String currency) {
        String normalized = (currency == null || currency.isBlank()) ? null : currency.trim().toUpperCase();
        if (normalized != null && !SUPPORTED_CURRENCIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Devise non supportée : « " + normalized + " ». "
                            + "Devises acceptées : " + String.join(", ", SUPPORTED_CURRENCIES.stream().sorted().toList())
                            + ". Laissez vide pour suivre la devise globale.");
        }
        User user = currentUser();
        user.setPreferredCurrency(normalized);
        user = userRepository.save(user);
        return user.getPreferredCurrency();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));
    }
}