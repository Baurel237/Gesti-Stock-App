package com.stock.api.controller;

import com.stock.api.dto.ResetDataRequest;
import com.stock.api.service.ResetDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;

/**
 * Point de contrôle pour vider les données de démonstration et recréer le
 * superadmin demandé (superadmin@mail.com / admin1232).
 *
 * Accessible uniquement aux SUPER_ADMIN.
 * La requête doit contenir un champ confirmation explicite pour éviter
 * une exécution accidentelle.
 *
 * ⚠️ Garde-fou d'environnement : cet endpoint DÉTRUIT des données et recrée
 * un compte connu. Il est neutralisé (404) sauf si le profil actif est
 * explicitement « dev » ou « test » — jamais disponible sur un déploiement
 * de production, même pour un SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/reset")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Réinitialisation système", description = "Réinitialisation contrôlée des données de démo")
public class ResetDataController {

    private final ResetDataService resetDataService;
    private final Environment environment;

    /** Profils dans lesquels la réinitialisation destructrice est autorisée. */
    private static final Set<String> ALLOWED_PROFILES = Set.of("dev", "test");

    private static final String CONFIRM_TOKEN = "RESET-NOW";

    @PostMapping("/db")
    @Operation(summary = "Réinitialiser les données de démo",
               description = "Vide les utilisateurs et données de démo, puis recrée le superadmin demandé. " +
                             "Uniquement disponible quand un profil dev ou test est actif (neutralisé en production).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réinitialisation effectuée"),
            @ApiResponse(responseCode = "403", description = "Accès refusé (pas superadmin)"),
            @ApiResponse(responseCode = "404", description = "Endpoint neutralisé (profil non dev/test)")
    })
    public ResponseEntity<ResetDataService.ResetResult> reset(@RequestBody ResetDataRequest request) {
        // Set.copyOf(Arrays.asList(...)) : liste vide si aucun profil actif.
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        // Aucun profil autorisé (production, profil postgresql seul, etc.) → 404.
        if (activeProfiles.stream().noneMatch(ALLOWED_PROFILES::contains)) {
            log.warn("Réinitialisation système refusée : profil(s) actif(s) {} non autorisé(s)",
                    activeProfiles.isEmpty() ? "aucun" : activeProfiles);
            return ResponseEntity.notFound().build();
        }

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
