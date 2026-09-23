package com.stock.api.service;

import com.stock.api.dto.OrderLineRequest;
import com.stock.api.dto.OrderLineResponse;
import com.stock.api.dto.OrderRequest;
import com.stock.api.dto.OrderResponse;
import com.stock.api.entity.Order;
import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.entity.OrderLine;
import com.stock.api.entity.Product;
import com.stock.api.entity.StockMovement;
import com.stock.api.entity.User;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.tenant.TenantContext;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de gestion des commandes (US-09, US-10).
 * - Création multi-lignes avec calcul automatique du total
 * - Gestion des statuts avec transitions contrôlées
 * - Déclenchement des mouvements de stock à la validation (US-10)
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * US-09 : Liste paginée des commandes avec filtres.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(OrderStatus status, Long createdById, Pageable pageable) {
        return orderRepository.findByFilters(status, createdById, pageable)
                .map(this::toResponse);
    }

    /**
     * US-09 : Détail d'une commande.
     */
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));
        return toResponse(order);
    }

    /**
     * US-09 : Création d'une commande multi-lignes.
     * Le montant total est calculé automatiquement.
     */
    @Transactional
    public OrderResponse create(OrderRequest request, String userEmail) {
        // Charger l'utilisateur
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        TenantGuard.assertSameCompany(companyId);

        // Générer une référence unique
        String reference = generateReference();

        // Créer la commande
        Order order = Order.builder()
                .reference(reference)
                .createdBy(user)
                .companyId(companyId)
                .status(OrderStatus.PENDING)
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
     * US-10 : Validation d'une commande.
     * Déclenche les mouvements de SORTIE de stock pour chaque ligne
     * (la validation confirme la sortie des marchandises).
     * RG-02 : rejet si le stock est insuffisant pour une ligne.
     */
    @Transactional
    public OrderResponse validate(Long id, String userEmail) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("La commande '%s' ne peut pas être validée (statut: %s). "
                                    + "Seule une commande en attente peut être validée.",
                            order.getReference(), order.getStatus()));
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));

        // Créer les mouvements de sortie de stock pour chaque ligne
        for (OrderLine line : order.getLines()) {
            Product product = line.getProduct();

            // RG-02 : rejet si stock insuffisant pour cette ligne
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
     * - PENDING → CANCELLED (simple annulation)
     * - VALIDATED → CANCELLED (annulation avec RESTITUTION du stock :
     *   les quantités sorties à la validation sont remises en stock)
     * - CANCELLED → error (déjà annulée)
     */
    @Transactional
    public OrderResponse cancel(Long id, String userEmail) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée avec l'id: " + id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    String.format("La commande '%s' est déjà annulée",
                            order.getReference()));
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur non trouvé avec l'email: " + userEmail));

        // Si la commande était validée, restituer le stock (annulation des sorties)
        if (order.getStatus() == OrderStatus.VALIDATED) {
            for (OrderLine line : order.getLines()) {
                Product product = line.getProduct();

                StockMovement movement = StockMovement.builder()
                        .type(StockMovement.MovementType.ENTRY)
                        .product(product)
                        .quantity(line.getQuantity())
                        .reason("Restitution liée à l'annulation de la commande " + order.getReference())
                        .performedBy(user)
                        .order(order)
                        .build();

                stockMovementRepository.save(movement);

                product.addQuantity(line.getQuantity());
                productRepository.save(product);
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
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .lines(lineResponses)
                .createdById(order.getCreatedBy().getId())
                .createdByEmail(order.getCreatedBy().getEmail())
                .createdByName(order.getCreatedBy().getFirstName() + " " + order.getCreatedBy().getLastName())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
