package com.stock.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Point d'entrée personnalisé pour les erreurs d'authentification (401).
 * Retourne un body JSON au lieu d'une redirection HTML.
 *
 * Distingue les cas pour un message explicite :
 * - pas de token → « jeton d'authentification manquant »
 * - token invalide/expiré → « jeton invalide ou expiré »
 * - compte désactivé par l'administration → message dédié
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Attribut de requête posé par JwtAuthenticationFilter quand le compte est désactivé. */
    public static final String ACCOUNT_DISABLED_ATTRIBUTE = "com.stock.api.accountDisabled";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        boolean accountDisabled = Boolean.TRUE.equals(request.getAttribute(ACCOUNT_DISABLED_ATTRIBUTE));
        boolean hasToken = request.getHeader("Authorization") != null
                && request.getHeader("Authorization").startsWith("Bearer ");

        String message;
        String details;
        if (accountDisabled) {
            message = "Votre compte a été désactivé. Contactez un administrateur.";
            details = "Le compte associé à ce jeton n'est plus actif.";
        } else if (hasToken) {
            message = "Votre session a expiré ou le jeton est invalide. Reconnectez-vous.";
            details = "Le jeton fourni est invalide ou expiré.";
        } else {
            message = "Authentification requise. Fournissez un jeton dans l'en-tête « Authorization: Bearer <token> ».";
            details = "Aucun jeton d'authentification n'a été fourni avec la requête.";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Non authentifié");
        body.put("message", message);
        body.put("details", details);
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
