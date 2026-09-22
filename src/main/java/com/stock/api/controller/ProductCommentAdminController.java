package com.stock.api.controller;

import com.stock.api.dto.ProductCommentResponse;
import com.stock.api.service.ProductCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal des commentaires produits, réservé à l'administration.
 */
@RestController
@RequestMapping("/api/admin/product-comments")
@RequiredArgsConstructor
@Tag(name = "Commentaires produits (admin)", description = "Journal des alertes laissées par les vendeurs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
public class ProductCommentAdminController {

    private final ProductCommentService productCommentService;

    @GetMapping
    @Operation(summary = "Lister tous les commentaires produits",
               description = "Journal complet des alertes laissées par les vendeurs, les plus récentes d'abord")
    public ResponseEntity<Page<ProductCommentResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(productCommentService.findAll(pageable));
    }
}
