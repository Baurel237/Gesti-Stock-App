package com.stock.api.controller;

import com.stock.api.dto.CategoryRequest;
import com.stock.api.dto.CategoryResponse;
import com.stock.api.service.CategoryService;
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
 * US-04 : CRUD complet des catégories.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Catégories", description = "Gestion des catégories de produits")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * US-04 : Liste paginée des catégories.
     */
    @GetMapping
    @Operation(summary = "Lister les catégories",
               description = "Retourne la liste paginée des catégories actives")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public ResponseEntity<Page<CategoryResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(categoryService.findAll(pageable));
    }

    /**
     * US-04 : Détail d'une catégorie.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Obtenir une catégorie",
               description = "Retourne les détails d'une catégorie par son ID")
    @ApiResponse(responseCode = "200", description = "Catégorie trouvée")
    @ApiResponse(responseCode = "404", description = "Catégorie non trouvée")
    public ResponseEntity<CategoryResponse> findById(
            @Parameter(description = "ID de la catégorie") @PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    /**
     * US-04 : Création d'une catégorie.
     */
    @PostMapping
    @Operation(summary = "Créer une catégorie",
               description = "Crée une nouvelle catégorie de produits")
    @ApiResponse(responseCode = "201", description = "Catégorie créée avec succès")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Nom de catégorie déjà utilisé")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-04 : Mise à jour d'une catégorie.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour une catégorie",
               description = "Modifie les informations d'une catégorie existante")
    @ApiResponse(responseCode = "200", description = "Catégorie mise à jour")
    @ApiResponse(responseCode = "404", description = "Catégorie non trouvée")
    @ApiResponse(responseCode = "409", description = "Nom de catégorie déjà utilisé")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<CategoryResponse> update(
            @Parameter(description = "ID de la catégorie") @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    /**
     * US-04 : Suppression logique (RG-04).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une catégorie",
               description = "Supprime logiquement une catégorie (RG-04). Interdite si des produits y sont rattachés.")
    @ApiResponse(responseCode = "204", description = "Catégorie supprimée")
    @ApiResponse(responseCode = "404", description = "Catégorie non trouvée")
    @ApiResponse(responseCode = "409", description = "Des produits sont encore rattachés")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID de la catégorie") @PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
