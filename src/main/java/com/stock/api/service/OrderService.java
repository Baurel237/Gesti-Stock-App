package com.stock.api.service;

import com.stock.api.dto.OrderLineRequest;
import com.stock.api.dto.OrderLineResponse;
import com.stock.api.dto.OrderRequest;
import com.stock.api.dto.OrderResponse;
import com.stock.api.entity.Order;
import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.entity.Order.OrderType;
import com.stock.api.entity.OrderLine;
import com.stock.api.entity.Product;
import com.stock.api.entity.Role;
import com.stock.api.entity.StockMovement;
import com.stock.api.entity.Supplier;
import com.stock.api.entity.User;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.SupplierRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.repository.WarehouseRepository;
import com.stock.api.entity.Warehouse;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de gestion des commandes (US-09, US-10, V10).
 * - Création multi-lignes avec calcul automatique du total
 * - Gestion des statuts avec transitions contrôlées
 * - À la validation (V10) :
 *   • PURCHASE (commande fournisseur) → mouvements d'ENTRÉE de stock
 *   • STANDARD / CLIENT → mouvements de SORTIE (RG-02 : stock insuffisant rejeté)
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * US-09 : Liste paginée des commandes avec filtres.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(OrderStatus status, OrderType type, Long createdById, Pageable pageable) {
        return orderRepository.findByFilters(TenantGuard.companyScope(), status, type, createdById, pageable)
                .map(this::toResponse);
    }

    /**
     * US-09 : Détail d'une commande.
     */
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));
        TenantGuard.assertSameCompany(order.getCompanyId());
        return toResponse(order);
    }

    /**
     * US-09 : Création d'une commande multi-lignes.
     * Le montant total est calculé automatiquement.
     * V10 : le type détermine les champs obligatoires et l'impact stock à la validation.
     */
    @Transactional
    public OrderResponse create(OrderRequest request, String userEmail) {
        // Charger l'utilisateur
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        TenantGuard.assertSameCompany(companyId);

        OrderType type = request.getType() != null ? request.getType() : OrderType.STANDARD;

        // PURCHASE : réservée aux profils de gestion (SUPER_ADMIN/ADMIN/MANAGER)
        if (type == OrderType.PURCHASE && !hasRole(user, Role.SUPER_ADMIN, Role.ADMIN, Role.MANAGER)) {
            throw new BusinessRuleException("Seul un administrateur ou un manager peut passer une commande fournisseur.");
        }

        // Résoudre le fournisseur (PURCHASE uniquement)
        Supplier supplier = null;
        if (type == OrderType.PURCHASE) {
            if (request.getSupplierId() == null) {
                throw new BusinessRuleException("Une commande fournisseur exige un fournisseur.");
            }
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Fournisseur non trouvé avec l'id: " + request.getSupplierId()));
            if (supplier.isDeleted()) {
                throw new BusinessRuleException("Fournisseur « " + supplier.getName() + " » supprimé.");
            }
            TenantGuard.assertSameCompany(supplier.getCompanyId());
        }

        // CLIENT : nom du client + date de livraison obligatoires
        if (type == OrderType.CLIENT) {
            if (request.getClientName() == null || request.getClientName().isBlank()) {
                throw new BusinessRuleException("Une commande client exige le nom du client.");
            }
            if (request.getDeliveryDate() == null) {
                throw new BusinessRuleException("Une commande client exige une date de livraison.");
            }
            if (request.getDeliveryDate().isBefore(LocalDate.now())) {
                throw new BusinessRuleException("La date de livraison ne peut pas être dans le passé.");
            }
        }

        // Entrepôt / boutique d'origine (V11) : pour un vendeur affecté à un
        // entrepôt, la commande y est rattachée automatiquement (ticket).
        Warehouse warehouse = resolveWarehouse(user);

        // Générer une référence unique
        String reference = generateReference();

        // Créer la commande
        Order order = Order.builder()
                .reference(reference)
                .createdBy(user)
                .companyId(companyId)
                .status(OrderStatus.PENDING)
                .orderType(type)
                .supplier(supplier)
                .warehouse(warehouse)
                .clientName(type == OrderType.CLIENT ? request.getClientName().trim() : null)
                .deliveryDate(type == OrderType.CLIENT ? request.getDeliveryDate() : null)
                .notes(request.getNotes())
                .lines(new ArrayList<>())
                .build();

        // Ajouter les lignes de commande
        for (OrderLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Produit non trouvé avec l'id: " + lineRequest.getProductId()));

            TenantGuard.assertSameCompany(product.getCompanyId());

            if (product.isDeleted()) {
                throw new IllegalArgumentException("Produit supprimé: " + product.getName());
            }

            OrderLine line = OrderLine.builder()
                    .product(product)
                    .quantity(lineRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(product.getPrice().multiply(
                            java.math.BigDecimal.valueOf(lineRequest.getQuantity())))
                    .build();

            order.addLine(line);
        }

        order = orderRepository.save(order);
        return toResponse(order);
    }

    /**
     * V10-US vendeur : modification d'une commande en attente par son créateur.
     * - Seule une commande PENDING est modifiable (une fois validée, le stock a bougé) ;
     * - Seul le créateur (ou un profil de gestion) peut modifier sa commande ;
     * - Seuls clientName / deliveryDate / lines / notes sont modifiables —
     *   type et fournisseur restent figés après création ;
     * - Le total est recalculé automatiquement.
     */
    @Transactional
    public OrderResponse update(Long id, OrderRequest request, String userEmail) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));
        TenantGuard.assertSameCompany(order.getCompanyId());

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));

        boolean isCreator = order.getCreatedBy().getId().equals(user.getId());
        boolean isManager = hasRole(user, Role.SUPER_ADMIN, Role.ADMIN, Role.MANAGER);
        if (!isCreator && !isManager) {
            throw new BusinessRuleException("Seul le créateur de la commande ou un profil de gestion peut la modifier.");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("La commande '%s' ne peut pas être modifiée (statut: %s). "
                                    + "Seule une commande en attente est modifiable.",
                            order.getReference(), order.getStatus()));
        }

        // CLIENT : nom du client + date de livraison obligatoires, date non passée
        if (order.getOrderType() == OrderType.CLIENT) {
            if (request.getClientName() == null || request.getClientName().isBlank()) {
                throw new BusinessRuleException("Une commande client exige le nom du client.");
            }
            if (request.getDeliveryDate() == null) {
                throw new BusinessRuleException("Une commande client exige une date de livraison.");
            }
            if (request.getDeliveryDate().isBefore(LocalDate.now())) {
                throw new BusinessRuleException("La date de livraison ne peut pas être dans le passé.");
            }
            order.setClientName(request.getClientName().trim());
            order.setDeliveryDate(request.getDeliveryDate());
        }

        // Remplacer les lignes (orphanRemoval = true) et recalculer le total
        order.getLines().clear();
        for (OrderLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Produit non trouvé avec l'id: " + lineRequest.getProductId()));

            TenantGuard.assertSameCompany(product.getCompanyId());

            if (product.isDeleted()) {
                throw new IllegalArgumentException("Produit supprimé: " + product.getName());
            }

            OrderLine line = OrderLine.builder()
                    .product(product)
                    .quantity(lineRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(product.getPrice().multiply(
                            java.math.BigDecimal.valueOf(lineRequest.getQuantity())))
                    .build();

            order.addLine(line);
        }

        order.setNotes(request.getNotes());
        order = orderRepository.save(order);
        return toResponse(order);
    }

    /**
     * US-10 : Validation d'une commande en attente.
     * V10 — impact stock selon le type :
     * - PURCHASE : mouvements d'ENTRÉE (fournisseur rattaché) + augmentation du stock
     * - STANDARD / CLIENT : mouvements de SORTIE (RG-02 : rejet si stock insuffisant)
     */
    @Transactional
    public OrderResponse validate(Long id, String userEmail) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));
        TenantGuard.assertSameCompany(order.getCompanyId());

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("La commande '%s' ne peut pas être validée (statut: %s). "
                                    + "Seule une commande en attente peut être validée.",
                            order.getReference(), order.getStatus()));
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));

        boolean purchase = order.getOrderType() == OrderType.PURCHASE;

        for (OrderLine line : order.getLines()) {
            Product product = line.getProduct();

            if (purchase) {
                StockMovement movement = StockMovement.builder()
                        .type(StockMovement.MovementType.ENTRY)
                        .product(product)
                        .quantity(line.getQuantity())
                        .reason("Entrée liée à la commande fournisseur " + order.getReference())
                        .performedBy(user)
                        .companyId(order.getCompanyId())
                        .supplier(order.getSupplier())
                        .order(order)
                        .build();

                stockMovementRepository.save(movement);

                // RG-01 : le stock ne peut pas être négatif — ici il augmente.
                product.addQuantity(line.getQuantity());
                productRepository.save(product);
                continue;
            }

            // RG-02 : rejet si stock insuffisant pour cette ligne (STANDARD / CLIENT)
            if (!product.canRemoveQuantity(line.getQuantity())) {
                throw new IllegalStateException(
                        String.format("Stock insuffisant pour le produit '%s' (réf. %s). "
                                        + "Disponible : %d unité(s), demandé : %d unité(s).",
                                product.getName(), product.getReference(),
                                product.getQuantity(), line.getQuantity()));
            }

            StockMovement movement = StockMovement.builder()
                    .type(StockMovement.MovementType.EXIT)
                    .product(product)
                    .quantity(line.getQuantity())
                    .reason("Sortie liée à la commande " + order.getReference())
                    .performedBy(user)
                    .companyId(order.getCompanyId())
                    .order(order)
                    .build();

            stockMovementRepository.save(movement);

            // RG-01 : décrémenter la quantité du produit (jamais négative)
            product.removeQuantity(line.getQuantity());
            productRepository.save(product);
        }

        // Valider la commande
        order.transitionTo(OrderStatus.VALIDATED);
        order = orderRepository.save(order);

        return toResponse(order);
    }

    /**
     * US-09 : Annulation d'une commande.
     * V10 — restitution selon le type :
     * - PURCHASE validée → les quantités ENTRÉES sont retirées du stock (sorties)
     * - STANDARD / CLIENT validée → les quantités SORTIES sont restituées (entrées)
     */
    @Transactional
    public OrderResponse cancel(Long id, String userEmail) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));
        TenantGuard.assertSameCompany(order.getCompanyId());

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    String.format("La commande '%s' est déjà annulée",
                            order.getReference()));
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));

        // V10-US vendeur : le créateur peut annuler sa propre commande PENDING ;
        // sinon, réservé aux profils de gestion (SUPER_ADMIN/ADMIN/MANAGER).
        boolean isCreator = order.getCreatedBy().getId().equals(user.getId());
        boolean isManager = hasRole(user, Role.SUPER_ADMIN, Role.ADMIN, Role.MANAGER);
        if (!isCreator && !isManager) {
            throw new BusinessRuleException(
                    "Seul le créateur de la commande ou un profil de gestion peut l'annuler.");
        }
        if (!isManager && order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException(
                    "Une commande déjà validée ne peut être annulée que par un profil de gestion.");
        }

        if (order.getStatus() == OrderStatus.VALIDATED) {
            boolean purchase = order.getOrderType() == OrderType.PURCHASE;

            for (OrderLine line : order.getLines()) {
                Product product = line.getProduct();

                if (purchase) {
                    // Annulation d'un achat : les produits entrés sont retirés du stock
                    if (!product.canRemoveQuantity(line.getQuantity())) {
                        throw new IllegalStateException(
                                String.format("Impossible d'annuler : stock insuffisant pour retirer '%s' "
                                                + "(disponible: %d, à retirer: %d).",
                                        product.getName(), product.getQuantity(), line.getQuantity()));
                    }

                    StockMovement movement = StockMovement.builder()
                            .type(StockMovement.MovementType.EXIT)
                            .product(product)
                            .quantity(line.getQuantity())
                            .reason("Retour fournisseur lié à l'annulation de la commande " + order.getReference())
                            .performedBy(user)
                            .companyId(order.getCompanyId())
                            .order(order)
                            .build();

                    stockMovementRepository.save(movement);

                    product.removeQuantity(line.getQuantity());
                    productRepository.save(product);
                } else {
                    // Annulation d'une sortie : les quantités sorties sont restituées
                    StockMovement movement = StockMovement.builder()
                            .type(StockMovement.MovementType.ENTRY)
                            .product(product)
                            .quantity(line.getQuantity())
                            .reason("Restitution liée à l'annulation de la commande " + order.getReference())
                            .performedBy(user)
                            .companyId(order.getCompanyId())
                            .order(order)
                            .build();

                    stockMovementRepository.save(movement);

                    product.addQuantity(line.getQuantity());
                    productRepository.save(product);
                }
            }
        }

        order.transitionTo(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        return toResponse(order);
    }

    /**
     * Génère une référence unique de commande.
     * Format : CMD-YYYYMMDD-XXXX
     */
    private String generateReference() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniquePart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("CMD-%s-%s", datePart, uniquePart);
    }

    /**
     * V11 : entrepôt / boutique d'origine de la commande.
     * Si le créateur est affecté à un seul entrepôt actif, la commande y est
     * rattachée automatiquement ; profils multi-affectés ou non affectés → null.
     */
    private Warehouse resolveWarehouse(User user) {
        if (user.getWarehouses() == null) {
            return null;
        }
        java.util.Set<Warehouse> actives = user.getWarehouses().stream()
                .filter(Warehouse::isActive)
                .collect(java.util.stream.Collectors.toSet());
        return actives.size() == 1 ? actives.iterator().next() : null;
    }

    private boolean hasRole(User user, Role... roles) {
        if (user.getRoles() == null) {
            return false;
        }
        for (Role role : roles) {
            if (user.getRoles().contains(role)) {
                return true;
            }
        }
        return false;
    }

    private OrderResponse toResponse(Order order) {
        List<OrderLineResponse> lineResponses = order.getLines().stream()
                .map(line -> OrderLineResponse.builder()
                        .id(line.getId())
                        .productId(line.getProduct().getId())
                        .productName(line.getProduct().getName())
                        .quantity(line.getQuantity())
                        .unitPrice(line.getUnitPrice())
                        .subtotal(line.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .reference(order.getReference())
                .type(order.getOrderType())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .lines(lineResponses)
                .createdById(order.getCreatedBy().getId())
                .createdByEmail(order.getCreatedBy().getEmail())
                .createdByName(order.getCreatedBy().getFirstName() + " " + order.getCreatedBy().getLastName())
                .supplierId(order.getSupplier() != null ? order.getSupplier().getId() : null)
                .supplierName(order.getSupplier() != null ? order.getSupplier().getName() : null)
                .clientName(order.getClientName())
                .deliveryDate(order.getDeliveryDate())
                .warehouseId(order.getWarehouse() != null ? order.getWarehouse().getId() : null)
                .warehouseName(order.getWarehouse() != null ? order.getWarehouse().getName() : null)
                .warehouseLocation(order.getWarehouse() != null ? order.getWarehouse().getLocation() : null)
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}