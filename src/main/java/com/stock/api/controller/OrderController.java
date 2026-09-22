package com.stock.api.controller;

import com.stock.api.dto.OrderRequest;
import com.stock.api.dto.OrderResponse;
import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * US-09 : CRUD des commandes.
 * US-10 : Validation avec déclenchement des mouvements de stock.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Commandes", description = "Gestion des commandes")
public class OrderController {

    private final OrderService orderService;

    /**
     * US-09 : Liste paginée des commandes.
     */
    @GetMapping
    @Operation(summary = "Lister les commandes",
               description = "Retourne la liste paginée des commandes avec filtres optionnels (status, createdById)")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<Page<OrderResponse>> findAll(
            @Parameter(description = "Statut de la commande") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "ID du créateur") @RequestParam(required = false) Long createdById,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(status, createdById, pageable));
    }

    /**
     * US-09 : Détail d'une commande.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Obtenir une commande",
               description = "Retourne les détails d'une commande par son ID")
    @ApiResponse(responseCode = "200", description = "Commande trouvée")
    @ApiResponse(responseCode = "404", description = "Commande non trouvée")
    public ResponseEntity<OrderResponse> findById(
            @Parameter(description = "ID de la commande") @PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    /**
     * US-09 : Création d'une commande multi-lignes.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Créer une commande",
               description = "Crée une nouvelle commande avec une ou plusieurs lignes. " +
                       "Le montant total est calculé automatiquement.")
    @ApiResponse(responseCode = "201", description = "Commande créée")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody OrderRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        OrderResponse response = orderService.create(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-10 : Validation d'une commande.
     * Déclenche les mouvements de sortie de stock.
     */
    @PostMapping("/{id}/validate")
    @Operation(summary = "Valider une commande",
               description = "Valide une commande en attente et déclenche les sorties de stock. " +
                       "RG-02 : rejet si stock insuffisant.")
    @ApiResponse(responseCode = "200", description = "Commande validée")
    @ApiResponse(responseCode = "409", description = "Statut invalide ou stock insuffisant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<OrderResponse> validate(
            @Parameter(description = "ID de la commande") @PathVariable Long id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(orderService.validate(id, userEmail));
    }

    /**
     * US-09 : Annulation d'une commande.
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une commande",
               description = "Annule une commande en attente")
    @ApiResponse(responseCode = "200", description = "Commande annulée")
    @ApiResponse(responseCode = "409", description = "Statut invalide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<OrderResponse> cancel(
            @Parameter(description = "ID de la commande") @PathVariable Long id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(orderService.cancel(id, userEmail));
    }
}
