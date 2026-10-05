package com.stock.api.controller;

import com.stock.api.dto.SupplierRequest;
import com.stock.api.dto.SupplierResponse;
import com.stock.api.service.SupplierService;
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
import org.springframework.web.bind.annotation.*;

/**
 * Story 2.1 : CRUD des fournisseurs de l'entreprise du token.
 * Lecture ouverte aux utilisateurs authentifiés ; écriture réservée
 * à SUPER_ADMIN et ADMIN (décision RBAC de la story).
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "Fournisseurs", description = "Gestion des fournisseurs de l'entreprise")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @Operation(summary = "Lister les fournisseurs",
               description = "Liste paginée des fournisseurs actifs de l'entreprise, avec recherche facultative par nom (?search=...)")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<Page<SupplierResponse>> findAll(
            @Parameter(description = "Recherche par nom (facultatif)")
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(supplierService.findAll(search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un fournisseur",
               description = "Retourne le détail d'un fournisseur (doit appartenir à l'entreprise du token)")
    @ApiResponse(responseCode = "200", description = "Fournisseur trouvé")
    @ApiResponse(responseCode = "404", description = "Fournisseur non trouvé")
    public ResponseEntity<SupplierResponse> findById(
            @Parameter(description = "ID du fournisseur") @PathVariable Long id) {
        return ResponseEntity.ok(supplierService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un fournisseur",
               description = "Crée un fournisseur rattaché à l'entreprise du token")
    @ApiResponse(responseCode = "201", description = "Fournisseur créé")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé dans l'entreprise")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse response = supplierService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un fournisseur",
               description = "Modifie les informations d'un fournisseur existant")
    @ApiResponse(responseCode = "200", description = "Fournisseur mis à jour")
    @ApiResponse(responseCode = "404", description = "Fournisseur non trouvé")
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé dans l'entreprise")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<SupplierResponse> update(
            @Parameter(description = "ID du fournisseur") @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un fournisseur",
               description = "Suppression logique (RG-04) : le fournisseur disparaît des listes, l'historique est conservé")
    @ApiResponse(responseCode = "204", description = "Fournisseur supprimé")
    @ApiResponse(responseCode = "404", description = "Fournisseur non trouvé")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID du fournisseur") @PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
