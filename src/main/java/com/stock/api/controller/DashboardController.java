package com.stock.api.controller;

import com.stock.api.dto.DashboardStatsResponse;
import com.stock.api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agrégats du tableau de bord.
 * Remplace la pagination complète des produits côté client :
 * la valeur du stock est un SUM en base au lieu de N requêtes.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Agrégats du tableau de bord")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Statistiques du tableau de bord",
               description = "Retourne les agrégats calculés côté base : nombre de produits, "
                       + "valeur totale du stock, catégories, commandes en attente, stock bas")
    @ApiResponse(responseCode = "200", description = "Statistiques retournées")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}
