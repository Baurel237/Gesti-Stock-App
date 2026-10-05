package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private boolean active;

    /** Entreprise de rattachement (null = compte plateforme). */
    private Long companyId;
    private String companyName;

    /** Entrepôts affectés (story 1.1) — vide si aucune restriction. */
    private Set<Long> warehouseIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
