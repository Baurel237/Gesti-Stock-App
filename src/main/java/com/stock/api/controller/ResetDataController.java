package com.stock.api.controller;

import com.stock.api.dto.ResetDataRequest;
import com.stock.api.service.ResetDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point de contrôle pour vider les données de démonstration et recréer le
 * superadmin demandé (superadmin@mail.com / admin1232).
 *
 * Accessible uniquement aux SUPER_ADMIN.
 * La requête doit contenir un champ confirmation explicite pour éviter
 * une exécution accidentelle.
 */
@RestController
@RequestMapping("/api/admin/reset")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Réinitialisation système", description = "Réinitialisation contrôlée des données de démo")
public class ResetDataController {

    private final ResetDataService resetDataService;

    private static final String CONFIRM_TOKEN = "RESET-NOW";

    @PostMapping("/db")
    @Operation(summary = "Réinitialiser les données de démo",
               description = "Vide les utilisateurs et données de démo, puis recrée le superadmin demandé.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réinitialisation effectuée"),
            @ApiResponse(responseCode = "403", description = "Accès refusé (pas superadmin)")
    })
    public ResponseEntity<ResetDataService.ResetResult> reset(@RequestBody ResetDataRequest request) {
        if (!CONFIRM_TOKEN.equals(request.getConfirmation())) {
            throw new IllegalArgumentException("Confirmation invalide");
        }

        log.info("Réinitialisation système demandée par : {}",
                SecurityContextHolder.getContext().getAuthentication().getName());

        ResetDataService.ResetResult result = resetDataService.reset(false, true);
        log.info("Réinitialisation terminée : {}", result);

        return ResponseEntity.ok(result);
    }
}
