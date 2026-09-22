package com.stock.api.controller;

import com.stock.api.dto.UserPreferenceResponse;
import com.stock.api.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Préférences personnelles de l'utilisateur connecté (self-service).
 * La devise d'affichage est propre à chaque utilisateur : un vendeur peut
 * afficher ses montants en FCFA, EUR, USD... sans affecter les autres.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Mes préférences", description = "Préférences personnelles (devise d'affichage)")
public class MeController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping("/currency")
    @Operation(summary = "Devise d'affichage de l'utilisateur connecté",
               description = "Retourne la devise ISO 4217 choisie, ou null si l'utilisateur suit la devise globale")
    public ResponseEntity<UserPreferenceResponse> getCurrency() {
        return ResponseEntity.ok(
                UserPreferenceResponse.builder()
                        .currency(userPreferenceService.getCurrency())
                        .build());
    }

    @PutMapping("/currency")
    @Operation(summary = "Changer sa devise d'affichage",
               description = "Met à jour la devise d'affichage de l'utilisateur connecté. Passer null ou vide pour revenir à la devise globale")
    public ResponseEntity<UserPreferenceResponse> updateCurrency(
            @RequestBody UserPreferenceResponse.UpdateRequest request) {
        return ResponseEntity.ok(
                UserPreferenceResponse.builder()
                        .currency(userPreferenceService.updateCurrency(request.getCurrency()))
                        .build());
    }
}