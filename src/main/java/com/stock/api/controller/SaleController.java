package com.stock.api.controller;

import com.stock.api.dto.*;
import com.stock.api.entity.Sale.SaleStatus;
import com.stock.api.service.SaleService;
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
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@Tag(name = "Ventes", description = "Gestion des ventes par les vendeurs")
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'SELLER')")
    @Operation(summary = "Créer une vente", description = "Enregistre une nouvelle vente avec décrément automatique du stock (sortie de stock)")
    @ApiResponse(responseCode = "201", description = "Vente créée")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Stock insuffisant")
    public ResponseEntity<SaleResponse> create(
            @Valid @RequestBody SaleRequest request,
            Authentication authentication) {
        SaleResponse response = saleService.create(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Lister les ventes", description = "Retourne la liste paginée des ventes avec filtres. Un vendeur ne voit que ses propres ventes ; les admins/managers voient tout et peuvent filtrer par vendeur.")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<Page<SaleResponse>> findAll(
            @Parameter(description = "ID du vendeur") @RequestParam(required = false) Long sellerId,
            @Parameter(description = "Statut de la vente") @RequestParam(required = false) SaleStatus status,
            Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(saleService.findAll(sellerId, status, pageable, authentication.getName()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'SELLER')")
    @Operation(summary = "Annuler une vente",
               description = "Le vendeur peut annuler sa vente pendant 4 heures après l'enregistrement. "
                       + "Passé ce délai, l'annulation est réservée au SUPER_ADMIN/ADMIN (ou passe par une demande). "
                       + "Le stock des articles est remis en stock.")
    @ApiResponse(responseCode = "200", description = "Vente annulée")
    @ApiResponse(responseCode = "400", description = "Délai d'auto-annulation dépassé ou statut invalide")
    @ApiResponse(responseCode = "403", description = "Vente d'un autre vendeur")
    public ResponseEntity<SaleResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(saleService.cancel(id, authentication.getName()));
    }

    @PostMapping("/{id}/cancellation-request")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'SELLER')")
    @Operation(summary = "Demander l'annulation d'une vente",
               description = "Le vendeur demande l'annulation de sa vente après le délai d'auto-annulation (4 h). "
                       + "La demande est traitée par SUPER_ADMIN/ADMIN via /{id}/cancel ou /admin/cancellation-requests/{id}/reject.")
    @ApiResponse(responseCode = "200", description = "Demande enregistrée")
    @ApiResponse(responseCode = "400", description = "Vente encore dans le délai d'auto-annulation ou demande déjà en attente")
    public ResponseEntity<SaleResponse> requestCancellation(
            @PathVariable Long id,
            @RequestBody(required = false) SaleCancellationRequest request,
            Authentication authentication) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(saleService.requestCancellation(id, authentication.getName(), reason));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'SELLER')")
    @Operation(summary = "Modifier une vente",
               description = "Le vendeur peut modifier sa vente pendant 4 heures après l'enregistrement. "
                       + "Passé ce délai, la modification est réservée au SUPER_ADMIN/ADMIN (ou passe par une demande). "
                       + "Le stock est recalculé (entrées/sorties) selon les nouveaux articles.")
    @ApiResponse(responseCode = "200", description = "Vente modifiée")
    @ApiResponse(responseCode = "400", description = "Délai dépassé, stock insuffisant ou statut invalide")
    @ApiResponse(responseCode = "403", description = "Vente d'un autre vendeur")
    public ResponseEntity<SaleResponse> edit(
            @PathVariable Long id,
            @Valid @RequestBody SaleRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(saleService.editSale(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/edit-request")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'SELLER')")
    @Operation(summary = "Demander la modification d'une vente",
               description = "Le vendeur demande la modification de sa vente après le délai d'auto-modification (4 h). "
                       + "La demande est traitée par SUPER_ADMIN/ADMIN via /admin/edit-requests/{id}/approve.")
    @ApiResponse(responseCode = "200", description = "Demande enregistrée")
    @ApiResponse(responseCode = "400", description = "Vente encore dans le délai ou demande déjà en attente")
    public ResponseEntity<SaleEditRequestResponse> requestEdit(
            @PathVariable Long id,
            @Valid @RequestBody SaleEditRequestData request,
            Authentication authentication) {
        return ResponseEntity.ok(saleService.requestSaleEdit(id, request, authentication.getName()));
    }

    @GetMapping("/admin/pending-cancellations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Demandes d'annulation en attente",
               description = "Ventes dont l'annulation a été demandée par un vendeur, à traiter")
    public ResponseEntity<List<SaleResponse>> getPendingCancellations() {
        return ResponseEntity.ok(saleService.getPendingCancellations());
    }

    @PostMapping("/admin/cancellation-requests/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Refuser une demande d'annulation",
               description = "Lève la demande d'annulation en attente ; la vente reste effectuée.")
    @ApiResponse(responseCode = "204", description = "Demande refusée")
    public ResponseEntity<Void> rejectCancellation(@PathVariable Long id, Authentication authentication) {
        saleService.rejectCancellation(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/pending-edit-requests")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Demandes de modification en attente",
               description = "Demandes de modification de ventes émises par les vendeurs, à traiter")
    public ResponseEntity<List<SaleEditRequestResponse>> getPendingEditRequests() {
        return ResponseEntity.ok(saleService.getPendingEditRequests());
    }

    @PostMapping("/admin/edit-requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Approuver une demande de modification",
               description = "Applique la modification demandée (stock recalculé) et marque la demande APPROVED.")
    @ApiResponse(responseCode = "200", description = "Vente modifiée")
    @ApiResponse(responseCode = "400", description = "Stock insuffisant, vente annulée ou demande déjà traitée")
    public ResponseEntity<SaleResponse> approveEditRequest(@PathVariable Long requestId, Authentication authentication) {
        return ResponseEntity.ok(saleService.approveEditRequest(requestId, authentication.getName()));
    }

    @PostMapping("/admin/edit-requests/{requestId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Refuser une demande de modification",
               description = "Marque la demande REJECTED ; la vente est inchangée.")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    public ResponseEntity<SaleEditRequestResponse> rejectEditRequest(@PathVariable Long requestId, Authentication authentication) {
        return ResponseEntity.ok(saleService.rejectEditRequest(requestId, authentication.getName()));
    }

    @GetMapping("/me")
    @Operation(summary = "Mes ventes", description = "Retourne les ventes de l'utilisateur authentifié (scoping automatique).")
    public ResponseEntity<Page<SaleResponse>> findMySales(
            @Parameter(description = "Statut de la vente") @RequestParam(required = false) SaleStatus status,
            Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(saleService.findMySales(status, pageable, authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir une vente", description = "Retourne les détails d'une vente (reçu imprimable). Un vendeur ne peut voir que ses propres ventes.")
    @ApiResponse(responseCode = "200", description = "Vente trouvée")
    @ApiResponse(responseCode = "404", description = "Vente non trouvée")
    public ResponseEntity<SaleResponse> findById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(saleService.findById(id, authentication.getName()));
    }

    @GetMapping("/seller/{sellerId}/stats")
    @Operation(summary = "Stats vendeur", description = "Retourne les statistiques du tableau de bord vendeur. Un vendeur ne peut consulter que ses propres statistiques.")
    @ApiResponse(responseCode = "200", description = "Statistiques retournées")
    public ResponseEntity<SellerDashboardStats> getSellerStats(@PathVariable Long sellerId, Authentication authentication) {
        return ResponseEntity.ok(saleService.getSellerStats(sellerId, authentication.getName()));
    }

    @GetMapping("/admin/seller-stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Stats vendeurs (admin)", description = "Retourne les statistiques comparatives de tous les vendeurs sur la période")
    @ApiResponse(responseCode = "200", description = "Statistiques retournées")
    public ResponseEntity<List<SellerStatsResponse>> getAdminSellerStats(
            @Parameter(description = "Date de début")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "Date de fin")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(saleService.getAdminSellerStats(start, end));
    }

    @GetMapping("/admin/sellers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Liste des vendeurs avec stats", description = "Liste tous les vendeurs actifs avec leurs statistiques sur la période (y compris sans vente)")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<List<SellerStatsResponse>> getSellersWithStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        LocalDateTime effectiveStart = start != null ? start : LocalDateTime.now().minusMonths(1);
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now();
        return ResponseEntity.ok(saleService.getSellersWithStats(effectiveStart, effectiveEnd));
    }

    @GetMapping("/admin/live-feed")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Flux des ventes en direct", description = "Dernières ventes réalisées, pour le suivi en temps réel par l'admin")
    @ApiResponse(responseCode = "200", description = "Flux retourné")
    public ResponseEntity<List<SaleResponse>> getLiveFeed(
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(saleService.getLiveSalesFeed(Math.min(Math.max(limit, 1), 100), authentication.getName()));
    }

    @GetMapping("/admin/daily-sales")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Ventes quotidiennes (admin)", description = "Retourne l'agrégation des ventes par jour")
    @ApiResponse(responseCode = "200", description = "Données retournées")
    public ResponseEntity<List<SellerDashboardStats.DailySalesData>> getDailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(saleService.getDailySalesAggregation(start, end));
    }
}
