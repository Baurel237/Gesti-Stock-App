package com.stock.api.service;

import com.stock.api.entity.AuditLog;
import com.stock.api.entity.User;
import com.stock.api.repository.AuditLogRepository;
import com.stock.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Service de traçabilité : enregistre chaque action métier dans audit_logs.
 * REQUIRES_NEW pour que l'entrée d'audit survive même si la transaction
 * métier est annulée (rollback).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Enregistre une action d'audit pour l'utilisateur authentifié courant,
     * ou "system" si le contexte n'est pas authentifié (seed, démarrage...).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Long entityId, String entityName, String details) {
        User actor = resolveCurrentUser();

        String actorEmail = actor != null ? actor.getEmail() : "system";
        String actorRoles = actor != null
                ? actor.getRoles().stream().map(Enum::name).sorted().collect(Collectors.joining(","))
                : "SYSTEM";

        save(actorEmail, actorRoles, action, entityType, entityId, entityName, details);
    }

    /**
     * Enregistre une action pour un utilisateur explicite.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFor(String actorEmail, String actorRoles, String action,
                          String entityType, Long entityId, String entityName, String details) {
        save(actorEmail, actorRoles, action, entityType, entityId, entityName, details);
    }

    private User resolveCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
                return null;
            }
            return userRepository.findByEmail(auth.getName()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void save(String actorEmail, String actorRoles, String action,
                      String entityType, Long entityId, String entityName, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .actorEmail(actorEmail)
                    .actorRoles(actorRoles)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .details(details)
                    .build());
            log.debug("Audit: {} {} {} par {}", action, entityType, entityName, actorEmail);
        } catch (Exception e) {
            // Ne jamais faire échouer la transaction métier à cause de l'audit
            log.error("Erreur lors de l'enregistrement d'audit : {}", e.getMessage());
        }
    }
}
