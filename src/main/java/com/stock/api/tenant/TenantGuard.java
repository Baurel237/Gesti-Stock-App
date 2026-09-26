package com.stock.api.tenant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
        Long current = companyScope();
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
        Long current = companyScope();
        if (current == null) {
            throw new AccessDeniedException(
                    "Opération réservée aux utilisateurs rattachés à une entreprise.");
        }
        return current;
    }

    /**
     * Retourne la portée courante. Une portée nulle signifie plateforme et
     * n'est autorisée que pour SUPER_ADMIN quand une authentification existe.
     * L'absence d'Authentication est tolérée pour les traitements internes et
     * les tests unitaires qui ne traversent pas Spring Security.
     */
    public static Long companyScope() {
        Long current = TenantContext.getCompanyId();
        if (current != null) {
            return current;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        boolean platformAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        if (!platformAdmin) {
            throw new AccessDeniedException("Une portée plateforme est réservée au SUPER_ADMIN.");
        }
        return null;
    }
}
