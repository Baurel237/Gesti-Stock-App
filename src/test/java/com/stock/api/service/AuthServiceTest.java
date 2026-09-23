package com.stock.api.service;

import com.stock.api.dto.AuthResponse;
import com.stock.api.dto.LoginRequest;
import com.stock.api.dto.RefreshTokenRequest;
import com.stock.api.entity.Role;
import com.stock.api.entity.User;
import com.stock.api.repository.UserRepository;
import com.stock.api.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour AuthService.
 * Couvre : US-02 (connexion) et US-03 (erreurs).
 * L'inscription a été retirée : les comptes sont créés par l'administration.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private LoginRequest validLoginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        validLoginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        savedUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("$2a$10$encoded-password")
                .firstName("Jean")
                .lastName("Dupont")
                .roles(Set.of(Role.USER))
                .active(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // refresh() — Renouvellement de session
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("refresh() — Renouvellement de session")
    class RefreshTests {

        private final UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("test@example.com")
                .password("$2a$10$encoded-password")
                .authorities("ROLE_USER")
                .build();

        @Test
        @DisplayName("Refresh token valide → nouveaux tokens (rotation)")
        void refresh_success() {
            when(jwtService.extractEmail("valid-refresh")).thenReturn("test@example.com");
            when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
            when(jwtService.isTokenValid("valid-refresh", userDetails)).thenReturn(true);
            when(jwtService.generateToken(userDetails)).thenReturn("new-access");
            when(jwtService.generateRefreshToken(userDetails)).thenReturn("new-refresh");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));

            AuthResponse response = authService.refresh(
                    RefreshTokenRequest.builder().refreshToken("valid-refresh").build());

            assertEquals("new-access", response.getToken());
            assertEquals("new-refresh", response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());
        }

        @Test
        @DisplayName("Refresh token mal formé → AuthRefreshException")
        void refresh_corruptedToken() {
            when(jwtService.extractEmail("corrupted")).thenThrow(new ExpiredJwtException(null, null, "expired"));

            assertThrows(AuthRefreshException.class,
                    () -> authService.refresh(RefreshTokenRequest.builder().refreshToken("corrupted").build()));

            verify(userDetailsService, never()).loadUserByUsername(any());
        }

        @Test
        @DisplayName("Refresh token expiré → AuthRefreshException")
        void refresh_expiredToken() {
            when(jwtService.extractEmail("expired-refresh")).thenReturn("test@example.com");
            when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
            when(jwtService.isTokenValid("expired-refresh", userDetails)).thenReturn(false);

            assertThrows(AuthRefreshException.class,
                    () -> authService.refresh(RefreshTokenRequest.builder().refreshToken("expired-refresh").build()));

            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Compte désactivé → AuthRefreshException (pas de renouvellement)")
        void refresh_disabledAccount() {
            UserDetails disabled = org.springframework.security.core.userdetails.User.builder()
                    .username("test@example.com")
                    .password("$2a$10$encoded-password")
                    .disabled(true)
                    .authorities("ROLE_USER")
                    .build();
            when(jwtService.extractEmail("disabled-refresh")).thenReturn("test@example.com");
            when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(disabled);
            when(jwtService.isTokenValid("disabled-refresh", disabled)).thenReturn(true);

            assertThrows(AuthRefreshException.class,
                    () -> authService.refresh(RefreshTokenRequest.builder().refreshToken("disabled-refresh").build()));

            verify(jwtService, never()).generateToken(any());
        }
    }

    // ═══════════════════════════════════════════════════════
    // US-02 : Connexion et récupération d'un JWT
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("login() — Connexion")
    class LoginTests {

        @Test
        @DisplayName("Connexion réussie → JWT retourné")
        void login_success() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("test@example.com")
                    .password("$2a$10$encoded-password")
                    .authorities("ROLE_USER")
                    .build();
            when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("jwt-token-456");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));

            AuthResponse response = authService.login(validLoginRequest);

            assertNotNull(response);
            assertEquals("jwt-token-456", response.getToken());
            assertEquals("Bearer", response.getTokenType());
            assertEquals("test@example.com", response.getEmail());

            verify(authenticationManager).authenticate(any());
            verify(userDetailsService).loadUserByUsername("test@example.com");
            verify(jwtService).generateToken(userDetails);
        }

        @Test
        @DisplayName("Mauvais mot de passe → BadCredentialsException")
        void login_wrongPassword() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Email ou mot de passe incorrect"));

            assertThrows(BadCredentialsException.class,
                    () -> authService.login(validLoginRequest));

            verify(authenticationManager).authenticate(any());
            verify(userDetailsService, never()).loadUserByUsername(any());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Email inexistant → BadCredentialsException")
        void login_unknownEmail() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Email ou mot de passe incorrect"));

            LoginRequest unknownRequest = LoginRequest.builder()
                    .email("unknown@example.com")
                    .password("password123")
                    .build();

            assertThrows(BadCredentialsException.class,
                    () -> authService.login(unknownRequest));

            verify(authenticationManager).authenticate(any());
        }

        @Test
        @DisplayName("L'utilisateur est bien chargé par email")
        void login_loadsUserByEmail() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("test@example.com")
                    .password("$2a$10$encoded-password")
                    .authorities("ROLE_USER")
                    .build();
            when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("token");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));

            authService.login(validLoginRequest);

            verify(userDetailsService).loadUserByUsername("test@example.com");
            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("Le token JWT est bien généré après connexion")
        void login_generatesJwtToken() {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("test@example.com")
                    .password("$2a$10$encoded-password")
                    .authorities("ROLE_USER")
                    .build();
            when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("login-token");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));

            AuthResponse response = authService.login(validLoginRequest);

            assertEquals("login-token", response.getToken());
            verify(jwtService).generateToken(userDetails);
        }

        @Test
        @DisplayName("Les rôles de l'utilisateur sont bien dans la réponse")
        void login_rolesInResponse() {
            User multiRoleUser = User.builder()
                    .id(3L)
                    .email("multi@example.com")
                    .password("$2a$10$encoded")
                    .firstName("Multi")
                    .lastName("Role")
                    .roles(Set.of(Role.USER, Role.MANAGER))
                    .active(true)
                    .build();

            LoginRequest multiRoleRequest = LoginRequest.builder()
                    .email("multi@example.com")
                    .password("password123")
                    .build();

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(null);

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username("multi@example.com")
                    .password("$2a$10$encoded")
                    .authorities("ROLE_USER", "ROLE_MANAGER")
                    .build();
            when(userDetailsService.loadUserByUsername("multi@example.com")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("token");
            when(userRepository.findByEmail("multi@example.com")).thenReturn(Optional.of(multiRoleUser));

            AuthResponse response = authService.login(multiRoleRequest);

            assertTrue(response.getRoles().contains("USER"));
            assertTrue(response.getRoles().contains("MANAGER"));
            assertEquals(2, response.getRoles().size());
        }
    }
}
