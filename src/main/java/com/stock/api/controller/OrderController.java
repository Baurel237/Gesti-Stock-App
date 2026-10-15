package com.stock.api.controller;

import com.stock.api.dto.OrderRequest;
import com.stock.api.dto.OrderResponse;
import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.entity.Order.OrderType;
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
               description = "Retourne la liste paginée des commandes avec filtres optionnels (status, type, createdById)")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<Page<OrderResponse>> findAll(
            @Parameter(description = "Statut de la commande") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Type de commande (STANDARD/PURCHASE/CLIENT)") @RequestParam(required = false) OrderType type,
            @Parameter(description = "ID du créateur") @RequestParam(required = false) Long createdById,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(status, type, createdById, pageable));
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
     * Le type détermine le comportement (STANDARD : sortie, PURCHASE : entrée
     * fournisseur, CLIENT : commande vendeur avec client + date de livraison).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'WAREHOUSE_ADMIN', 'MANAGER', 'SELLER')")
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
     * V10-US vendeur : modification d'une commande en attente par son créateur
     * (ou un profil de gestion). Seuls clientName / deliveryDate / lignes / notes.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Modifier une commande",
               description = "Modifie une commande en attente : nom du client, date de livraison, " +
                       "lignes et notes (type et fournisseur figés). Réservé au créateur ou aux profils de gestion.")
    @ApiResponse(responseCode = "200", description = "Commande modifiée")
    @ApiResponse(responseCode = "403", description = "Ni créateur ni profil de gestion")
    @ApiResponse(responseCode = "409", description = "Statut non modifiable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'WAREHOUSE_ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<OrderResponse> update(
            @Parameter(description = "ID de la commande") @PathVariable Long id,
            @Valid @RequestBody OrderRequest request,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(orderService.update(id, request, userEmail));
    }

    /**
     * US-10 : Validation d'une commande.
     * V10 — impact stock selon le type :
     * PURCHASE → entrées de stock ; STANDARD/CLIENT → sorties (RG-02).
     */
    @PostMapping("/{id}/validate")
    @Operation(summary = "Valider une commande",
               description = "Valide une commande en attente : entrées de stock pour une " +
                       "commande fournisseur, sorties pour une commande interne ou client. " +
                       "RG-02 : rejet si stock insuffisant.")
    @ApiResponse(responseCode = "200", description = "Commande validée")
    @ApiResponse(responseCode = "409", description = "Statut invalide ou stock insuffisant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'WAREHOUSE_ADMIN', 'MANAGER')")
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
               description = "Annule une commande : le créateur peut annuler la sienne tant qu'elle est " +
                       "en attente ; les profils de gestion peuvent annuler toute commande (avec restitution stock)")
    @ApiResponse(responseCode = "200", description = "Commande annulée")
    @ApiResponse(responseCode = "403", description = "Ni créateur ni profil de gestion")
    @ApiResponse(responseCode = "409", description = "Statut invalide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'WAREHOUSE_ADMIN', 'MANAGER', 'SELLER')")
    public ResponseEntity<OrderResponse> cancel(
            @Parameter(description = "ID de la commande") @PathVariable Long id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(orderService.cancel(id, userEmail));
    }
}
