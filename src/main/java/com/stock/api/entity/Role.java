package com.stock.api.entity;

import lombok.Getter;

@Getter
public enum Role {
    SUPER_ADMIN("Super Administrateur"),
    ADMIN("Administrateur"),
    MANAGER("Manager"),
    GESTIONNAIRE("Gestionnaire"),
    SELLER("Vendeur"),
    USER("Utilisateur"),
    VIEWER("Observateur");

    private final String description;

    Role(String description) {
        this.description = description;
    }
}
