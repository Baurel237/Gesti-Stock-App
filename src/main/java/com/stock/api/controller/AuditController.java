package com.stock.api.controller;

import com.stock.api.dto.AuditLogResponse;
import com.stock.api.entity.AuditLog;
import com.stock.api.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * Journal d'audit — consultation réservée aux administrateurs.
 * Toutes les actions métier (produits, mouvements, commandes, ventes,
 * utilisateurs) y sont retraçables : qui, quoi, quand.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Journal de traçabilité des actions métier")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "Consulter le journal d'audit",
               description = "Retourne les entrées d'audit filtrables par auteur, action, type d'entité et dates")
    public ResponseEntity<Page<AuditLogResponse>> findAll(
            @Parameter(description = "Email de l'auteur") @RequestParam(required = false) String actorEmail,
            @Parameter(description = "Type d'action (CREATE, UPDATE, DELETE, ENTRY, EXIT, VALIDATE, CANCEL)")
            @RequestParam(required = false) String action,
            @Parameter(description = "Type d'entité (Product, Category, StockMovement, Order, Sale, User)")
            @RequestParam(required = false) String entityType,
            Pageable pageable) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorEmail != null && !actorEmail.isBlank()) {
                predicates.add(cb.equal(root.get("actorEmail"), actorEmail));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLogResponse> page = auditLogRepository.findAll(spec, pageable).map(this::toResponse);
        return ResponseEntity.ok(page);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorEmail(log.getActorEmail())
                .actorRoles(log.getActorRoles())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entityName(log.getEntityName())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
