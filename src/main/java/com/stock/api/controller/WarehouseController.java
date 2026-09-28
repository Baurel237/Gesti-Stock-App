package com.stock.api.controller;

import com.stock.api.dto.WarehouseRequest;
import com.stock.api.dto.WarehouseResponse;
import com.stock.api.dto.WarehouseStockResponse;
import com.stock.api.dto.WarehouseTransferRequest;
import com.stock.api.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module entrepôts OPTIONNEL (activable par entreprise via
 * Company.warehouseEnabled). Le stock global (Product.quantity) reste la
 * source de vérité : les entrepôts en décomposent la répartition.
 *
 * Endpoints :
 *  - CRUD des entrepôts de l'entreprise du token ;
 *  - stock d'un produit réparti par entrepôt ;
 *  - transfert de stock entre deux entrepôts de la même entreprise.
 *
 * Les entrées/sorties de stock ciblant un entrepôt passent par
 * POST /api/stock-movements avec un warehouseId (StockMovementRequest).
 */
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Entrepôts", description = "Gestion optionnelle des entrepôts et du stock par entrepôt")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "Lister les entrepôts de l'entreprise",
               description = "Retourne tous les entrepôts (actifs et inactifs) de l'entreprise du token")
    public ResponseEntity<List<WarehouseResponse>> findAll() {
        return ResponseEntity.ok(warehouseService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un entrepôt",
               description = "Retourne le détail d'un entrepôt (doit appartenir à l'entreprise du token)")
    public ResponseEntity<WarehouseResponse> findById(
            @Parameter(description = "ID de l'entrepôt") @PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un entrepôt",
               description = "Crée un entrepôt. Exige que le module entrepôts soit activé pour l'entreprise.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrepôt créé"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "409", description = "Module entrepôts non activé ou nom déjà utilisé")
    })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse response = warehouseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un entrepôt",
               description = "Modifie le nom, l'adresse ou l'état actif d'un entrepôt")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<WarehouseResponse> update(
            @Parameter(description = "ID de l'entrepôt") @PathVariable Long id,
            @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(warehouseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un entrepôt",
               description = "Suppression physique, autorisée uniquement si l'entrepôt est vide (aucun stock)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entrepôt supprimé"),
            @ApiResponse(responseCode = "409", description = "Entrepôt non vide")
    })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID de l'entrepôt") @PathVariable Long id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/product/{productId}/stock")
    @Operation(summary = "Stock d'un produit par entrepôt",
               description = "Répartition du stock d'un produit sur tous les entrepôts de l'entreprise")
    public ResponseEntity<List<WarehouseStockResponse>> getStockForProduct(
            @Parameter(description = "ID du produit") @PathVariable Long productId) {
        return ResponseEntity.ok(warehouseService.getStockForProduct(productId));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transférer du stock entre deux entrepôts",
               description = "Décrémente la source, incrémente la destination et trace deux mouvements. " +
                             "Ne modifie pas le stock global du produit.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Transfert effectué"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "409", description = "Stock insuffisant ou règle métier violée")
    })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'GESTIONNAIRE')")
    public ResponseEntity<Void> transfer(@Valid @RequestBody WarehouseTransferRequest request) {
        String userEmail = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        warehouseService.transfer(request, userEmail);
        return ResponseEntity.noContent().build();
    }
}
