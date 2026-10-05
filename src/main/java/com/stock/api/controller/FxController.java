package com.stock.api.controller;

import com.stock.api.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nouvelle exigence devise : expose les taux de change réels récupérés par
 * le backend depuis ExchangeRate-API (open.er-api.com), mis en cache 12 h.
 * Authentifié : aucun appel externe depuis le navigateur.
 */
@RestController
@RequestMapping("/api/fx")
@RequiredArgsConstructor
@Tag(name = "Taux de change", description = "Taux réels (API ExchangeRate) pour la conversion d'affichage")
public class FxController {

    private final FxRateService fxRateService;

    @GetMapping("/rates")
    @Operation(summary = "Taux de change réels",
               description = "Taux des devises supportées vis-à-vis de la devise de base (cache serveur 12 h, source ExchangeRate-API)")
    @ApiResponse(responseCode = "200", description = "Taux retournés")
    public ResponseEntity<FxRateService.FxRates> getRates(
            @Parameter(description = "Devise de base (défaut : EUR)")
            @RequestParam(defaultValue = "EUR") String base) {
        return ResponseEntity.ok(fxRateService.getRates(base));
    }
}
