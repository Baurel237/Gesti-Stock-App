package com.stock.api.service;

import com.stock.api.dto.WarehouseRequest;
import com.stock.api.dto.WarehouseResponse;
import com.stock.api.dto.WarehouseStockResponse;
import com.stock.api.dto.WarehouseTransferRequest;
import com.stock.api.entity.Company;
import com.stock.api.entity.Product;
import com.stock.api.entity.StockMovement;
import com.stock.api.entity.User;
import com.stock.api.entity.Warehouse;
import com.stock.api.entity.WarehouseStock;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.CompanyRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.repository.WarehouseRepository;
import com.stock.api.repository.WarehouseStockRepository;
import com.stock.api.tenant.TenantContext;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module entrepôts OPTIONNEL (V2) :
 *  - CRUD des entrepôts d'une entreprise (Company.warehouseEnabled requis) ;
 *  - consultation du stock par entrepôt ;
 *  - transfert de stock entre deux entrepôts de la même entreprise.
 * Le stock global Product.quantity reste la source de vérité : un transfert
 * interne ne change ni le total produit ni les ventes du mode stock simple.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        Long companyId = TenantGuard.requireCompanyId();
        return warehouseRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)
                .stream().map(w -> toResponse(w, companyId)).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entrepôt non trouvé avec l'id: " + id));
        TenantGuard.assertSameCompany(warehouse.getCompany().getId());
        return toResponse(warehouse, warehouse.getCompany().getId());
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        Long companyId = TenantGuard.requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Entreprise introuvable"));

        if (!company.isWarehouseEnabled()) {
            throw new BusinessRuleException(
                    "Le module entrepôts n'est pas activé pour cette entreprise.");
        }
        String name = request.getName().trim();
        if (warehouseRepository.existsByCompanyIdAndNameAndActiveTrue(companyId, name)) {
            throw new BusinessRuleException("Un entrepôt actif porte déjà ce nom.");
        }

        Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
                .company(company)
                .name(name)
                .location(request.getLocation())
                .active(true)
                .build());

        auditService.record("CREATE", "Warehouse", warehouse.getId(), warehouse.getName(),
                "Entrepôt créé" + (request.getLocation() != null ? " — " + request.getLocation() : ""));
        return toResponse(warehouse, companyId);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entrepôt non trouvé avec l'id: " + id));
        TenantGuard.assertSameCompany(warehouse.getCompany().getId());

        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            if (!name.equals(warehouse.getName())
                    && warehouseRepository.existsByCompanyIdAndNameAndActiveTrue(
                            warehouse.getCompany().getId(), name)) {
                throw new BusinessRuleException("Un entrepôt actif porte déjà ce nom.");
            }
            warehouse.setName(name);
        }
        if (request.getLocation() != null) {
            warehouse.setLocation(request.getLocation());
        }
        if (request.getActive() != null) {
            warehouse.setActive(request.getActive());
        }

        Warehouse saved = warehouseRepository.save(warehouse);
        auditService.record("UPDATE", "Warehouse", saved.getId(), saved.getName(),
                "Entrepôt mis à jour");
        return toResponse(saved, saved.getCompany().getId());
    }

    /**
     * Suppression PHYSIQUE autorisée seulement si l'entrepôt est vide
     * (aucun stock) ; sinon on refuse (données d'historique conservées).
     */
    @Transactional
    public void delete(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entrepôt non trouvé avec l'id: " + id));
        TenantGuard.assertSameCompany(warehouse.getCompany().getId());

        long stocked = warehouseStockRepository.findByWarehouseIdOrderByProductId(id).stream()
                .filter(s -> s.getQuantity() > 0)
                .count();
        if (stocked > 0) {
            throw new BusinessRuleException(
                    "Impossible de supprimer un entrepôt contenant encore du stock. Videz-le (transferts) puis réessayez.");
        }

        warehouseStockRepository.deleteByWarehouseId(id);
        warehouseRepository.delete(warehouse);
        auditService.record("DELETE", "Warehouse", id, warehouse.getName(),
                "Entrepôt supprimé (vide)");
    }

    /** Stock par entrepôt pour un produit donné (entreprise du token). */
    @Transactional(readOnly = true)
    public List<WarehouseStockResponse> getStockForProduct(Long productId) {
        Long companyId = TenantGuard.requireCompanyId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé avec l'id: " + productId));
        TenantGuard.assertSameCompany(product.getCompanyId());

        List<Warehouse> warehouses = warehouseRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        Map<Long, WarehouseStock> stockByWarehouse = warehouseStockRepository.findByProductId(productId)
                .stream()
                .collect(Collectors.toMap(s -> s.getWarehouse().getId(), Function.identity()));

        return warehouses.stream()
                .map(w -> {
                    WarehouseStock stock = stockByWarehouse.get(w.getId());
                    return WarehouseStockResponse.builder()
                            .warehouseId(w.getId())
                            .warehouseName(w.getName())
                            .quantity(stock != null ? stock.getQuantity() : 0)
                            .build();
                })
                .toList();
    }

    /**
     * Transfert de stock entre deux entrepôts DE LA MÊME ENTREPRISE.
     * Décrémente la source, incrémente la destination, trace deux mouvements.
     * Ne touche PAS au total produit (transfert interne).
     */
    @Transactional
    public void transfer(WarehouseTransferRequest request, String userEmail) {
        Long companyId = TenantGuard.requireCompanyId();

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessRuleException("La quantité de transfert doit être supérieure à 0.");
        }
        if (request.getFromWarehouseId().equals(request.getToWarehouseId())) {
            throw new BusinessRuleException("Les entrepôts source et destination doivent être différents.");
        }

        Warehouse from = warehouseRepository.findById(request.getFromWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entrepôt source non trouvé avec l'id: " + request.getFromWarehouseId()));
        Warehouse to = warehouseRepository.findById(request.getToWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entrepôt destination non trouvé avec l'id: " + request.getToWarehouseId()));
        TenantGuard.assertSameCompany(from.getCompany().getId());
        TenantGuard.assertSameCompany(to.getCompany().getId());

        if (!from.isActive() || !to.isActive()) {
            throw new BusinessRuleException("Le transfert exige deux entrepôts actifs.");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Produit non trouvé avec l'id: " + request.getProductId()));
        TenantGuard.assertSameCompany(product.getCompanyId());

        WarehouseStock source = getOrCreateStock(product, from, companyId);
        if (!source.canRemove(request.getQuantity())) {
            throw new BusinessRuleException(String.format(
                    "Stock insuffisant dans « %s » : disponible %d, demandé %d.",
                    from.getName(), source.getQuantity(), request.getQuantity()));
        }

        source.setQuantity(source.getQuantity() - request.getQuantity());
        warehouseStockRepository.save(source);

        WarehouseStock dest = getOrCreateStock(product, to, companyId);
        dest.setQuantity(dest.getQuantity() + request.getQuantity());
        warehouseStockRepository.save(dest);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));
        String reason = String.format("Transfert %s → %s", from.getName(), to.getName());
        stockMovementRepository.save(StockMovement.builder()
                .type(StockMovement.MovementType.EXIT)
                .product(product)
                .quantity(request.getQuantity())
                .reason(reason)
                .performedBy(user)
                .companyId(companyId)
                .warehouse(from)
                .build());
        stockMovementRepository.save(StockMovement.builder()
                .type(StockMovement.MovementType.ENTRY)
                .product(product)
                .quantity(request.getQuantity())
                .reason(reason)
                .performedBy(user)
                .companyId(companyId)
                .warehouse(to)
                .build());

        auditService.record("TRANSFER", "WarehouseStock", product.getId(), product.getName(),
                String.format("%d unité(s) %s → %s par %s",
                        request.getQuantity(), from.getName(), to.getName(), userEmail));
    }

    private WarehouseStock getOrCreateStock(Product product, Warehouse warehouse, Long companyId) {
        return warehouseStockRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> warehouseStockRepository.save(WarehouseStock.builder()
                        .companyId(companyId)
                        .product(product)
                        .warehouse(warehouse)
                        .quantity(0)
                        .build()));
    }

    private WarehouseResponse toResponse(Warehouse warehouse, Long companyId) {
        int total = warehouseStockRepository.findByWarehouseIdOrderByProductId(warehouse.getId()).stream()
                .mapToInt(WarehouseStock::getQuantity)
                .sum();
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .location(warehouse.getLocation())
                .active(warehouse.isActive())
                .companyId(companyId)
                .totalQuantity(total)
                .createdAt(warehouse.getCreatedAt())
                .build();
    }
}
