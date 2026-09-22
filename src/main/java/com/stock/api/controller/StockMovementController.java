package com.stock.api.controller;

import com.stock.api.dto.StockMovementRequest;
import com.stock.api.dto.StockMovementResponse;
import com.stock.api.dto.StockMovementTotalsResponse;
import com.stock.api.entity.StockMovement.MovementType;
import com.stock.api.service.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * US-07 : Enregistrement des mouvements de stock.
 * US-08 : Consultation de l'historique filtrable.
 */
@RestController
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
@Tag(name = "Mouvements de stock", description = "Gestion des entrées et sorties de stock")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    @GetMapping("/totals")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Totaux des mouvements de stock",
               description = "Retourne les montants et nombres totaux des entrées et sorties")
    @ApiResponse(responseCode = "200", description = "Totaux retournés")
    public ResponseEntity<StockMovementTotalsResponse> getTotals() {
        return ResponseEntity.ok(stockMovementService.getTotals());
    }    /**
     * US-07 : Enregistrement d'un mouvement (entrée/sortie).
     * RBAC : GESTIONNAIRE → entrées uniquement ; SELLER → sorties uniquement ;
     * MANAGER et au-dessus → les deux.
     */
    @PostMapping
    @PreAuthorize("(hasRole('GESTIONNAIRE') and #request.type?.name() == 'ENTRY') or "
                + "(hasRole('SELLER') and #request.type?.name() == 'EXIT') or "
                + "hasRole('MANAGER')")
    @Operation(summary = "Enregistrer un mouvement de stock",
               description = "Enregistre une entrée ou une sortie de stock. "
                       + "RG-02 : rejet si sortie avec quantité insuffisante. "
                       + "GESTIONNAIRE : entrées uniquement. SELLER : sorties uniquement.")
    @ApiResponse(responseCode = "201", description = "Mouvement enregistré")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Quantité insuffisante pour une sortie")
    public ResponseEntity<StockMovementResponse> create(
            @Valid @RequestBody StockMovementRequest request,
            Authentication authentication) {
        // Extraire l'email de l'utilisateur depuis le JWT
        String userEmail = authentication.getName();
        StockMovementResponse response = stockMovementService.create(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-08 : Historique des mouvements d'un produit.
     */
    @GetMapping("/product/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Historique des mouvements d'un produit",
               description = "Retourne l'historique paginé des mouvements pour un produit donné")
    @ApiResponse(responseCode = "200", description = "Historique retourné")
    public ResponseEntity<Page<StockMovementResponse>> findByProductId(
            @Parameter(description = "ID du produit") @PathVariable Long productId,
            Pageable pageable) {
        return ResponseEntity.ok(stockMovementService.findByProductId(productId, pageable));
    }

    /**
     * US-08 : Historique filtrable.
     */
    @GetMapping("/filters")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Historique filtré des mouvements",
               description = "Retourne l'historique filtré par produit, type et dates")
    @ApiResponse(responseCode = "200", description = "Historique filtré retourné")
    public ResponseEntity<Page<StockMovementResponse>> findByFilters(
            @Parameter(description = "ID du produit") @RequestParam(required = false) Long productId,
            @Parameter(description = "Type de mouvement (ENTRY/EXIT)") @RequestParam(required = false) MovementType type,
            @Parameter(description = "Date de début") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "Date de fin") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            Pageable pageable) {
        return ResponseEntity.ok(stockMovementService.findByFilters(productId, type, fromDate, toDate, pageable));
    }
}