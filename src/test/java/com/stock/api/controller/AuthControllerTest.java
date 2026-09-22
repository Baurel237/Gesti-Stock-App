package com.stock.api.controller;

import com.stock.api.dto.AuthResponse;
import com.stock.api.dto.LoginRequest;
import com.stock.api.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour AuthController (pur Mockito, sans Spring context).
 * Couvre : US-01 (inscription), US-02 (connexion), US-03 (erreurs).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private LoginRequest validLoginRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        validLoginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        authResponse = AuthResponse.builder()
                .token("jwt-token-123")
                .tokenType("Bearer")
                .userId(1L)
                .email("test@example.com")
                .firstName("Jean")
                .lastName("Dupont")
                .roles(Set.of("USER"))
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // US-02 : Connexion
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("login() — Connexion")
    class LoginTests {

        @Test
        @DisplayName("Connexion réussie → 200 + JWT")
        void login_success() {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(authResponse);

            var response = authController.login(validLoginRequest);

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals("jwt-token-123", response.getBody().getToken());
            assertEquals("Bearer", response.getBody().getTokenType());
        }

        @Test
        @DisplayName("Mauvais mot de passe → exception propagée")
        void login_wrongPassword() {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new org.springframework.security.authentication.BadCredentialsException(
                            "Email ou mot de passe incorrect"));

            assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                    () -> authController.login(validLoginRequest));
        }

        @Test
        @DisplayName("Email inexistant → exception propagée")
        void login_unknownEmail() {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new org.springframework.security.authentication.BadCredentialsException(
                            "Email ou mot de passe incorrect"));

            LoginRequest unknownRequest = LoginRequest.builder()
                    .email("unknown@test.com")
                    .password("password123")
                    .build();

            assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                    () -> authController.login(unknownRequest));
        }
    }

    // ═══════════════════════════════════════════════════════
    // US-03 : Vérification de l'interaction avec AuthService
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Interaction avec AuthService")
    class InteractionTests {

        @Test
        @DisplayName("login() appelle bien authService.login()")
        void login_callsService() {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(authResponse);

            authController.login(validLoginRequest);

            verify(authService).login(validLoginRequest);
        }
    }
}
