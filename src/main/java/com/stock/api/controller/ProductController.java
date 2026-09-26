package com.stock.api.controller;

import com.stock.api.dto.ProductRequest;
import com.stock.api.dto.ProductResponse;
import com.stock.api.service.ProductService;
import com.stock.api.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * US-05 : CRUD des produits.
 * US-06 : Recherche et filtrage paginé.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Produits", description = "Gestion des produits du stock")
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    /**
     * US-06 : Liste paginée avec filtres optionnels.
     */
    @GetMapping
    @Operation(summary = "Lister les produits",
               description = "Retourne la liste paginée des produits avec filtres optionnels (categoryId, name)")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public ResponseEntity<Page<ProductResponse>> findAll(
            @Parameter(description = "ID de la catégorie") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Nom du produit (recherche partielle)") @RequestParam(required = false) String name,
            Pageable pageable) {
        return ResponseEntity.ok(productService.findAll(categoryId, name, pageable));
    }

    /**
     * US-05 : Détail d'un produit.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un produit",
               description = "Retourne les détails d'un produit par son ID")
    @ApiResponse(responseCode = "200", description = "Produit trouvé")
    @ApiResponse(responseCode = "404", description = "Produit non trouvé")
    public ResponseEntity<ProductResponse> findById(
            @Parameter(description = "ID du produit") @PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    /**
     * US-06 : Produits en stock bas.
     */
    @GetMapping("/low-stock")
    @Operation(summary = "Produits en stock bas",
               description = "Retourne les produits dont la quantité est inférieure ou égale au seuil d'alerte")
    @ApiResponse(responseCode = "200", description = "Liste retournée")
    public ResponseEntity<Page<ProductResponse>> findLowStock(Pageable pageable) {
        return ResponseEntity.ok(productService.findLowStock(pageable));
    }

    /**
     * US-05 : Création d'un produit.
     */
    @PostMapping
    @Operation(summary = "Créer un produit",
               description = "Crée un nouveau produit rattaché à une catégorie")
    @ApiResponse(responseCode = "201", description = "Produit créé avec succès")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Nom ou référence déjà utilisé")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * US-05 : Mise à jour d'un produit.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un produit",
               description = "Modifie les informations d'un produit existant")
    @ApiResponse(responseCode = "200", description = "Produit mis à jour")
    @ApiResponse(responseCode = "404", description = "Produit non trouvé")
    @ApiResponse(responseCode = "409", description = "Nom ou référence déjà utilisé")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> update(
            @Parameter(description = "ID du produit") @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    /**
     * Ajout ou remplacement de la photo d'un produit.
     */
    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> uploadImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        productImageService.upload(id, file);
        return ResponseEntity.ok(productService.findById(id));
    }

    /** Retourne une image uniquement après authentification et contrôle tenant. */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getImage(@PathVariable Long id) {
        ProductImageService.ProductImageFile image = productImageService.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(image.path()));
    }

    /** Supprime uniquement la photo, sans supprimer le produit. */
    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ProductResponse> deleteImage(@PathVariable Long id) {
        productImageService.remove(id);
        return ResponseEntity.ok(productService.findById(id));
    }

    /**
     * US-05 : Suppression logique (RG-04).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un produit",
               description = "Supprime logiquement un produit (RG-04)")
    @ApiResponse(responseCode = "204", description = "Produit supprimé")
    @ApiResponse(responseCode = "404", description = "Produit non trouvé")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID du produit") @PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
