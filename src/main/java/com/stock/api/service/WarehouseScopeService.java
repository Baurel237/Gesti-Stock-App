package com.stock.api.service;

import com.stock.api.entity.Role;
import com.stock.api.entity.User;
import com.stock.api.entity.Warehouse;
import com.stock.api.repository.UserRepository;
import com.stock.api.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Périmètre « 1 entrepôt = 1 boutique » (module entrepôts activé).
 *
 * Règles :
 *  - portée plateforme (SUPER_ADMIN sans entreprise) et ADMIN entreprise :
 *    aucun filtre — vue Direction Générale ;
 *  - WAREHOUSE_ADMIN / MANAGER / GESTIONNAIRE / SELLER / etc. :
 *    STRICTEMENT limités aux entrepôts affectés dans user_warehouses ;
 *    sans affectation, aucune donnée entrepôt n'est visible (boutique non
 *    désignée), sauf si l'entreprise n'a qu'un entrepôt actif (attribution
 *    implicite, évite le blocage des bases où un seul entrepôt existe).
 *
 * Toute donnée rattachée à un entrepôt (vente, mouvement, commande, stock)
 * doit passer par assertWarehouseAllowed : le frontend masque les menus,
 * le backend refuse.
 */
@Service
@RequiredArgsConstructor
public class WarehouseScopeService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    /** Rôles qui gardent une vue Direction Générale (tous les entrepôts). */
    private static final Set<Role> GLOBAL_VIEWER_ROLES = Set.of(Role.SUPER_ADMIN, Role.ADMIN);

    /**
     * Entrepôts autorisés pour l'utilisateur courant, dans la portée tenant
     * courante (module entrepôts actif). Liste vide = rien n'est visible
     * (aucun entrepôt désigné).
     */
    @Transactional(readOnly = true)
    public List<Warehouse> allowedWarehouses(Long companyId) {
        List<Warehouse> all = warehouseRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        return allowedAmong(all);
    }

    /** Filtre les entrepôts donnés sur le périmètre de l'utilisateur courant. */
    public List<Warehouse> allowedAmong(List<Warehouse> warehouses) {
        User current = CurrentUserHolder.currentUser(userRepository);
        if (isGlobalViewer(current)) {
            return warehouses;
        }

        Set<Long> assigned = assignedIds(current);
        if (!assigned.isEmpty()) {
            return warehouses.stream()
                    .filter(w -> assigned.contains(w.getId()))
                    .toList();
        }

        // Boutique implicite : si l'entreprise n'a (dans la liste fournie,
        // normalement tous ceux de l'entreprise) qu'un seul entrepôt actif,
        // c'est celui-là, sinon aucun.
        List<Warehouse> actives = warehouses.stream().filter(Warehouse::isActive).toList();
        return actives.size() == 1 ? actives : List.of();
    }

    /**
     * Vérifie que l'utilisateur courant peut accéder à l'entrepôt donné.
     * À appeler sur TOUTE ressource rattachée à un entrepôt.
     */
    public void assertWarehouseAllowed(Warehouse warehouse) {
        User current = CurrentUserHolder.currentUser(userRepository);
        if (isGlobalViewer(current)) {
            return;
        }
        Set<Long> assigned = assignedIds(current);
        if (assigned.isEmpty()) {
            List<Warehouse> implicit = allowedAmong(List.of(warehouse));
            if (implicit.isEmpty()) {
                throw new AccessDeniedException(
                        "Aucun entrepôt ne vous est affecté : contactez votre administration.");
            }
            return;
        }
        if (!assigned.contains(warehouse.getId())) {
            throw new AccessDeniedException(
                    "Entrepôt « " + warehouse.getName() + " » hors de votre périmètre.");
        }
    }

    /** True si l'utilisateur courant a une vue Direction Générale. */
    public boolean isGlobalViewer(User current) {
        return current == null
                || current.hasRole(Role.SUPER_ADMIN)
                || current.hasRole(Role.ADMIN)
                || current.getCompany() == null; // portée plateforme
    }

    /** True si l'un des rôles donnés implique une vue Direction Générale. */
    public static boolean hasGlobalRole(User user) {
        return user != null
                && (user.hasRole(Role.SUPER_ADMIN) || user.hasRole(Role.ADMIN));
    }

    /**
     * IDs des entrepôts affectés à l'utilisateur. Pour un non-global, sans
     * affectation, l'accès strict est appliqué par allowedAmong.
     */
    private Set<Long> assignedIds(User current) {
        if (current == null || current.getWarehouses() == null) {
            return Set.of();
        }
        return current.getWarehouses().stream()
                .map(Warehouse::getId)
                .collect(Collectors.toSet());
    }
}
