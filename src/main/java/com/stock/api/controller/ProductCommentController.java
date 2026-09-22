package com.stock.api.controller;

import com.stock.api.dto.ProductCommentRequest;
import com.stock.api.dto.ProductCommentResponse;
import com.stock.api.service.ProductCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Commentaires vendeurs sur les produits — canal d'alerte vers l'admin
 * (« stock presque épuisé », « les clients réclament »...).
 */
@RestController
@RequestMapping("/api/products/{productId}/comments")
@RequiredArgsConstructor
@Tag(name = "Commentaires produits", description = "Alertes des vendeurs vers l'administration")
public class ProductCommentController {

    private final ProductCommentService productCommentService;

    @PostMapping
    @Operation(summary = "Commenter un produit",
               description = "Un vendeur signale un problème ou une demande client sur un produit")
    @ApiResponse(responseCode = "201", description = "Commentaire enregistré")
    public ResponseEntity<ProductCommentResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody ProductCommentRequest request,
            Authentication authentication) {
        ProductCommentResponse response =
                productCommentService.create(productId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Derniers commentaires d'un produit")
    public ResponseEntity<List<ProductCommentResponse>> findByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productCommentService.findRecentByProduct(productId));
    }
}
