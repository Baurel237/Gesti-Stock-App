package com.stock.api.controller;

import com.stock.api.dto.AppSettingsResponse;
import com.stock.api.service.AppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Paramètres globaux de l'application.
 * - GET /api/settings : lecture pour tout utilisateur authentifié
 *   (le front-end a besoin de la devise pour formater les montants).
 * - PUT /api/settings : mise à jour réservée au SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Paramètres", description = "Configuration globale (devise, langue, alertes)")
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    @GetMapping
    @Operation(summary = "Lire les paramètres",
               description = "Paramètres globaux : devise d'affichage, langue, alertes stock")
    public ResponseEntity<AppSettingsResponse> get() {
        return ResponseEntity.ok(appSettingsService.get());
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Mettre à jour les paramètres",
               description = "Réservé au super administrateur")
    public ResponseEntity<AppSettingsResponse> update(
            @Valid @RequestBody AppSettingsResponse.UpdateRequest request) {
        return ResponseEntity.ok(appSettingsService.update(request));
    }
}
