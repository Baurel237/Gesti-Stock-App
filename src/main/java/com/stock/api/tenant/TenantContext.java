package com.stock.api.tenant;

/**
 * Contexte tenant (entreprise) de la requête courante.
 * Alimenté par le filtre JWT à partir du claim companyId du token :
 *   - companyId = id de l'entreprise → requête scopée (ADMIN / SELLER)
 *   - companyId = null              → portée plateforme (SUPER_ADMIN, voit tout)
 */
public final class TenantContext {

    private static final ThreadLocal<Long> COMPANY_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCompanyId(Long companyId) {
        COMPANY_ID.set(companyId);
    }

    /** null = portée plateforme (SUPER_ADMIN). */
    public static Long getCompanyId() {
        return COMPANY_ID.get();
    }

    public static boolean isPlatformScope() {
        return COMPANY_ID.get() == null;
    }

    public static void clear() {
        COMPANY_ID.remove();
    }
}
