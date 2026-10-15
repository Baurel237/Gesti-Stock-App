package com.stock.api.service;

import com.stock.api.dto.DashboardStatsResponse;
import com.stock.api.dto.WarehouseStatsResponse;
import com.stock.api.entity.Warehouse;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SaleRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.WarehouseRepository;
import com.stock.api.repository.WarehouseStockRepository;
import com.stock.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrégats du tableau de bord (US-06).
 * Chaque métrique est un agrégat SQL exécuté côté base — remplace la
 * pagination complète des produits côté client (N+1 requêtes HTTP).
 * Portée multi-entreprises : companyId du token, null = plateforme.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final SaleRepository saleRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseScopeService warehouseScopeService;

    /**
     * Agrégats du dashboard en 3 requêtes SQL (produits+valeur, catégories,
     * commandes en attente) — la valeur du stock est un SUM en base.
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        Long companyId = TenantContext.getCompanyId();

        List<Object[]> productAggregates = productRepository.getDashboardAggregates(companyId);
        Object[] row = productAggregates.isEmpty()
                ? new Object[]{0L, BigDecimal.ZERO}
                : productAggregates.get(0);

        long totalProducts = row[0] != null ? ((Number) row[0]).longValue() : 0;
        BigDecimal totalStockValue = row[1] != null
                ? new BigDecimal(row[1].toString())
                : BigDecimal.ZERO;

        return DashboardStatsResponse.builder()
                .totalProducts(totalProducts)
                .totalStockValue(totalStockValue)
                .totalCategories(categoryRepository.countByFilters(companyId))
                .pendingOrders(orderRepository.countPendingOrders(companyId))
                .lowStockProducts(productRepository.countLowStockProducts(companyId))
                .build();
    }

    /**
     * Statistiques d'une boutique (entrepôt). Accès vérifié contre le
     * périmètre strict : l'admin d'entrepôt et l'équipe de la boutique voient
     * leurs chiffres ; la Direction Générale consulte n'importe quelle
     * boutique de l'entreprise.
     */
    @Transactional(readOnly = true)
    public WarehouseStatsResponse getWarehouseStats(Long warehouseId) {
        Long companyId = TenantContext.getCompanyId();
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entrepôt non trouvé avec l'id: " + warehouseId));
        if (companyId == null || !companyId.equals(warehouse.getCompany().getId())) {
            throw new AccessDeniedException(
                    "Accès interdit : cet entrepôt appartient à une autre entreprise.");
        }
        warehouseScopeService.assertWarehouseAllowed(warehouse);
        return buildWarehouseStats(warehouse);
    }

    /**
     * Calcul des stats d'un entrepôt préalablement autorisé : utilisé par la
     * vue DG qui a déjà la liste des entrepôts en mémoire (évite le re-fetch
     * findById par boutique — pas de requête N+1 inutile).
     */
    private WarehouseStatsResponse buildWarehouseStats(Warehouse warehouse) {
        Long warehouseId = warehouse.getId();
        var stockLines = warehouseStockRepository
                .findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(warehouseId);
        long availableProducts = stockLines.stream()
                .filter(s -> s.getQuantity() != null && s.getQuantity() > 0).count();
        long totalUnits = stockLines.stream()
                .filter(s -> s.getQuantity() != null)
                .mapToInt(s -> Math.max(s.getQuantity(), 0)).sum();
        BigDecimal stockValue = stockLines.stream()
                .filter(s -> s.getQuantity() != null && s.getQuantity() > 0 && s.getProduct() != null)
                .map(s -> s.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WarehouseStatsResponse.builder()
                .warehouseId(warehouseId)
                .warehouseName(warehouse.getName())
                .availableProducts(availableProducts)
                .stockValue(stockValue)
                .totalUnits(totalUnits)
                .salesCount(saleRepository.countByWarehouseId(warehouseId))
                .salesTotalAmount(saleRepository.sumTotalByWarehouseId(warehouseId))
                .movementsCount(stockMovementRepository.countByWarehouseId(warehouseId))
                .build();
    }

    /**
     * Vue Direction Générale : stats de toutes les boutiques autorisées pour
     * l'utilisateur courant (ADMIN = toutes, admin d'entrepôt = la sienne).
     */
    @Transactional(readOnly = true)
    public List<WarehouseStatsResponse> getWarehouseStatsForScope() {
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null) {
            throw new BusinessRuleException(
                    "Statistiques par entrepôt réservées aux utilisateurs rattachés à une entreprise.");
        }
        return warehouseScopeService.allowedWarehouses(companyId).stream()
                .map(this::buildWarehouseStats)
                .toList();
    }
}
