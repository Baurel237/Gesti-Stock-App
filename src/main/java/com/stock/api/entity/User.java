package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entité représentant un utilisateur du système.
 * RG-05 : un utilisateur a toujours au moins un rôle actif.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    /**
     * Entreprise de rattachement (V2 multi-entreprises).
     * NULL = compte plateforme (SUPER_ADMIN) avec vue globale.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /**
     * RG-05 : au moins un rôle actif.
     * Utilise ElementCollection pour stocker les rôles dans une table séparée.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Devise d'affichage choisie par l'utilisateur (ISO 4217).
     * Null = suivre la devise globale des paramètres de l'application.
     */
    @Column(length = 3)
    private String preferredCurrency;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * RG-05 : vérifie qu'au moins un rôle est présent.
     */
    public boolean hasValidRoles() {
        return roles != null && !roles.isEmpty();
    }

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }
}
