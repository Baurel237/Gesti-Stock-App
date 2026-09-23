package com.stock.api.service;

import com.stock.api.dto.StockMovementRequest;
import com.stock.api.dto.StockMovementResponse;
import com.stock.api.dto.StockMovementTotalsResponse;
import com.stock.api.entity.Product;
import com.stock.api.entity.StockMovement;
import com.stock.api.entity.StockMovement.MovementType;
import com.stock.api.entity.User;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.repository.WarehouseRepository;
import com.stock.api.repository.WarehouseStockRepository;
import com.stock.api.entity.Warehouse;
import com.stock.api.entity.WarehouseStock;
import com.stock.api.tenant.TenantContext;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de gestion des mouvements de stock (US-07, US-08).
 * RG-01 : quantité jamais négative.
 * RG-02 : rejet des sorties si quantité insuffisante.
 */
@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository warehouseStockRepository;

    /**
     * US-08 : Historique paginé des mouvements d'un produit.
     */
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> findByProductId(Long productId, Pageable pageable) {
        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toResponse);
    }

    /** Stock par entrepôt d'un produit (module optionnel). */
    @Transactional(readOnly = true)
    public List<WarehouseStock> getStockByWarehouse(Long productId) {
        return warehouseStockRepository.findByProductId(productId);
    }

    /**
     * US-08 : Historique filtrable (produit, type, dates).
     */
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> findByFilters(Long productId, MovementType type,
                                                      LocalDateTime fromDate, LocalDateTime toDate,
                                                      Pageable pageable) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return stockMovementRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * US-07 : Enregistrement d'un mouvement de stock.
     * RG-02 : rejet si sortie avec quantité insuffisante.
     * Met à jour automatiquement la quantité du produit.
     */
    @Transactional
    public StockMovementResponse create(StockMovementRequest request, String userEmail) {
        // Charger le produit (isolation : doit appartenir à l'entreprise du token)
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Produit non trouvé avec l'id: " + request.getProductId()));
        TenantGuard.assertSameCompany(product.getCompanyId());

        if (product.isDeleted()) {
            throw new IllegalArgumentException("Produit supprimé");
        }

        // Charger l'utilisateur auteur par email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        TenantGuard.assertSameCompany(companyId);

        // Entrepôt optionnel (module activé uniquement)
        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Entrepôt non trouvé avec l'id: " + request.getWarehouseId()));
            TenantGuard.assertSameCompany(warehouse.getCompany().getId());
        }

        // RG-02 : vérifier la quantité disponible pour une sortie
        if (request.getType() == MovementType.EXIT) {
            if (warehouse != null) {
                WarehouseStock stock = warehouseStockRepository
                        .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                        .orElseThrow(() -> new IllegalStateException(
                                String.format("'%s' n'est pas stocké dans cet entrepôt.", product.getName())));
                if (!stock.canRemove(request.getQuantity())) {
                    throw new IllegalStateException(
                            String.format("Quantité insuffisante pour '%s' dans l'entrepôt '%s'. " +
                                            "Disponible: %d, Demandé: %d",
                                    product.getName(), warehouse.getName(), stock.getQuantity(), request.getQuantity()));
                }
            } else if (!product.canRemoveQuantity(request.getQuantity())) {
                throw new IllegalStateException(
                        String.format("Quantité insuffisante pour le produit '%s'. " +
                                "Disponible: %d, Demandé: %d",
                                product.getName(), product.getQuantity(), request.getQuantity()));
            }
        }

        // Créer le mouvement
        StockMovement movement = StockMovement.builder()
                .type(request.getType())
                .product(product)
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .performedBy(user)
                .companyId(companyId)
                .warehouse(warehouse)
                .build();

        movement = stockMovementRepository.save(movement);

        // Mettre à jour la quantité du produit (RG-01 : jamais négatif)
        if (request.getType() == MovementType.ENTRY) {
            product.addQuantity(request.getQuantity());
            if (warehouse != null) {
                final Warehouse targetWarehouse = warehouse;
                WarehouseStock stock = warehouseStockRepository
                        .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                        .orElseGet(() -> warehouseStockRepository.save(WarehouseStock.builder()
                                .companyId(companyId)
                                .product(product)
                                .warehouse(targetWarehouse)
                                .quantity(0)
                                .build()));
                stock.setQuantity(stock.getQuantity() + request.getQuantity());
                warehouseStockRepository.save(stock);
            }
        } else {
            if (warehouse != null) {
                WarehouseStock stock = warehouseStockRepository
                        .findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
                stock.setQuantity(stock.getQuantity() - request.getQuantity());
                warehouseStockRepository.save(stock);
            }
            product.removeQuantity(request.getQuantity());
        }
        productRepository.save(product);

        return toResponse(movement);
    }

    @Transactional(readOnly = true)
    public StockMovementTotalsResponse getTotals() {
        long entryCount = 0;
        long exitCount = 0;
        BigDecimal entryAmount = BigDecimal.ZERO;
        BigDecimal exitAmount = BigDecimal.ZERO;

        for (Object[] row : stockMovementRepository.getTotalsByType()) {
            String type = (String) row[0];
            BigDecimal amount = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            long count = row[2] != null ? ((Number) row[2]).longValue() : 0;

            if ("ENTRY".equals(type)) {
                entryAmount = amount;
                entryCount = count;
            } else if ("EXIT".equals(type)) {
                exitAmount = amount;
                exitCount = count;
            }
        }

        return StockMovementTotalsResponse.builder()
                .totalEntryAmount(entryAmount)
                .totalExitAmount(exitAmount)
                .totalEntryCount(entryCount)
                .totalExitCount(exitCount)
                .build();
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .type(movement.getType())
                .productId(movement.getProduct().getId())
                .productName(movement.getProduct().getName())
                .quantity(movement.getQuantity())
                .reason(movement.getReason())
                .performedById(movement.getPerformedBy().getId())
                .performedByEmail(movement.getPerformedBy().getEmail())
                .orderId(movement.getOrder() != null ? movement.getOrder().getId() : null)
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
