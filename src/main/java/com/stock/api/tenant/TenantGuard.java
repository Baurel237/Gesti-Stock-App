package com.stock.api.tenant;

import org.springframework.security.access.AccessDeniedException;

/**
 * Garde-fou d'isolation multi-entreprises.
 * Toute donnée par identifiant (produit 123, vente 456...) doit vérifier
 * qu'elle appartient à l'entreprise du jeton, sauf portée plateforme.
 */
public final class TenantGuard {

    private TenantGuard() {
    }

    /**
     * Lève AccessDeniedException si la ressource appartient à une autre
     * entreprise que celle du contexte courant. SUPER_ADMIN (scope
     * plateforme) a accès à tout.
     */
    public static void assertSameCompany(Long resourceCompanyId) {
        Long current = TenantContext.getCompanyId();
        if (current == null) {
            return; // SUPER_ADMIN : portée plateforme
        }
        if (resourceCompanyId == null || !resourceCompanyId.equals(current)) {
            throw new AccessDeniedException(
                    "Accès interdit : cette donnée appartient à une autre entreprise.");
        }
    }

    /** Entreprise du contexte ; AccessDenied si portée plateforme. */
    public static Long requireCompanyId() {
        Long current = TenantContext.getCompanyId();
        if (current == null) {
            throw new AccessDeniedException(
                    "Opération réservée aux utilisateurs rattachés à une entreprise.");
        }
        return current;
    }
}
