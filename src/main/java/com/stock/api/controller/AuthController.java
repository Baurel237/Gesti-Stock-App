package com.stock.api.controller;

import com.stock.api.dto.AuthResponse;
import com.stock.api.dto.LoginRequest;
import com.stock.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-02 : Connexion et récupération d'un JWT
 * US-03 : Gestion des erreurs d'authentification
 *
 * L'auto-inscription (US-01) a été retirée : les comptes sont créés
 * par l'administration via /api/admin/users.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion des utilisateurs")
public class AuthController {

    private final AuthService authService;

    /**
     * US-02 : Connexion et récupération d'un JWT.
     */
    @PostMapping("/login")
    @Operation(summary = "Connexion utilisateur",
               description = "Authentifie l'utilisateur et retourne un JWT valide")
    @ApiResponse(responseCode = "200", description = "Connexion réussie, JWT retourné")
    @ApiResponse(responseCode = "401", description = "Email ou mot de passe incorrect")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
