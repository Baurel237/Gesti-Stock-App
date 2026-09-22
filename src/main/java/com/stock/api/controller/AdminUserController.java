package com.stock.api.controller;

import com.stock.api.dto.CreateUserRequest;
import com.stock.api.dto.ResetPasswordRequest;
import com.stock.api.dto.ResetPasswordResponse;
import com.stock.api.dto.UpdateUserRequest;
import com.stock.api.dto.UserResponse;
import com.stock.api.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Administration Utilisateurs", description = "Gestion des utilisateurs et des rôles")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Lister tous les utilisateurs")
    public ResponseEntity<Page<UserResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(adminUserService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un utilisateur par ID")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur", description = "Crée un nouvel utilisateur avec des rôles")
    @ApiResponse(responseCode = "201", description = "Utilisateur créé")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur", description = "Met à jour les rôles, le nom ou le statut actif/inactif")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminUserService.update(id, request));
    }

    /**
     * Réinitialisation du mot de passe par l'administration : génère un mot de
     * passe temporaire si aucun n'est fourni, sinon applique celui transmis.
     */
    @PutMapping("/{id}/password")
    @Operation(summary = "Réinitialiser le mot de passe",
               description = "Définit un nouveau mot de passe (ou en génère un temporaire si vide) et le retourne en clair une seule fois")
    @ApiResponse(responseCode = "200", description = "Mot de passe réinitialisé")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ResetPasswordRequest request) {
        String newPassword = request != null ? request.getNewPassword() : null;
        return ResponseEntity.ok(adminUserService.resetPassword(id, newPassword));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un utilisateur")
    @ApiResponse(responseCode = "204", description = "Utilisateur supprimé")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
