package com.stock.api.service;

import com.stock.api.dto.AuthResponse;
import com.stock.api.dto.LoginRequest;
import com.stock.api.dto.RefreshTokenRequest;
import com.stock.api.entity.Role;
import com.stock.api.entity.User;
import com.stock.api.repository.UserRepository;
import com.stock.api.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service d'authentification : connexion.
 * US-02: Connexion et récupération d'un JWT
 *
 * Les comptes sont créés par l'administration (AdminUserService) ;
 * l'auto-inscription a été retirée.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    /**
     * US-02 : Connexion et récupération d'un JWT.
     */
    public AuthResponse login(LoginRequest request) {
        // Authentification via Spring Security AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Charger l'utilisateur et générer les tokens (accès 24h + refresh 7j)
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

        return buildAuthResponse(user, token, refreshToken);
    }

    /**
     * Refresh token : échange un refresh token valide contre un nouveau couple
     * token d'accès + refresh token (rotation). Le compte doit toujours être actif.
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        String email;
        try {
            email = jwtService.extractEmail(refreshToken);
        } catch (JwtException e) {
            throw new AuthRefreshException("Refresh token invalide");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Signature + expiration + compte actif : un compte désactivé ne peut
        // pas renouveler sa session, même avec un refresh token encore valide.
        if (!jwtService.isTokenValid(refreshToken, userDetails) || !userDetails.isEnabled()) {
            throw new AuthRefreshException("Refresh token invalide ou expiré");
        }

        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthRefreshException("Utilisateur non trouvé"));

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    private AuthResponse buildAuthResponse(User user, String token, String refreshToken) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream()
                        .map(Role::name)
                        .collect(Collectors.toSet()))
                .build();
    }
}
