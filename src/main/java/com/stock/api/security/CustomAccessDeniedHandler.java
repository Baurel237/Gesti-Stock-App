package com.stock.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire personnalisé des erreurs d'autorisation (403).
 * Sans lui, Spring Security renvoie une réponse vide (ou la page d'erreur
 * HTML par défaut) : le front-end n'a alors aucun message exploitable.
 *
 * Retourne un corps JSON indiquant le rôle de l'appelant, pour que
 * « accès refusé » devienne compréhensible (ex. : un SELLER qui tente de
 * supprimer un produit voit quels rôles sont requis).
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String roles = auth != null
                ? auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .collect(Collectors.joining(", "))
                : "anonyme";

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Accès refusé");
        body.put("message", accessDeniedException.getMessage() != null
                && !accessDeniedException.getMessage().isBlank()
                ? accessDeniedException.getMessage()
                : "Vous n'avez pas les droits nécessaires pour effectuer cette action.");
        body.put("details", "Vos rôles actuels : " + roles
                + ". Contactez un administrateur si vous pensez que c'est une erreur.");
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
