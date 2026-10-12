package com.stock.api.service;

import com.stock.api.dto.OrderLineRequest;
import com.stock.api.dto.OrderRequest;
import com.stock.api.dto.OrderResponse;
import com.stock.api.entity.Order;
import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.entity.Order.OrderType;
import com.stock.api.entity.Product;
import com.stock.api.entity.Role;
import com.stock.api.entity.Supplier;
import com.stock.api.entity.User;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.SupplierRepository;
import com.stock.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour OrderService.
 * RG-02 : rejet des sorties si quantité insuffisante à la validation.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .build();

        product = Product.builder()
                .id(1L)
                .name("Produit Test")
                .reference("REF-001")
                .quantity(10)
                .alertThreshold(5)
                .price(BigDecimal.valueOf(29.99))
                .deleted(false)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // Création de commande
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Création de commande")
    class CreateOrderTests {

        @Test
        @DisplayName("Création d'une commande multi-lignes → succès")
        void create_orderWithLines() {
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(1L);
                return order;
            });

            OrderLineRequest line = OrderLineRequest.builder()
                    .productId(1L)
                    .quantity(3)
                    .build();

            OrderRequest request = OrderRequest.builder()
                    .lines(List.of(line))
                    .notes("Commande test")
                    .build();

            OrderResponse response = orderService.create(request, "user@test.com");

            assertNotNull(response);
            assertEquals(OrderStatus.PENDING, response.getStatus());
            assertNotNull(response.getReference());
            assertTrue(response.getReference().startsWith("CMD-"));
            assertEquals(1, response.getLines().size());
            assertEquals(BigDecimal.valueOf(89.97), response.getTotalAmount());
        }

        @Test
        @DisplayName("Création avec produit supprimé → exception")
        void create_deletedProduct_throwsException() {
            product.setDeleted(true);
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            OrderLineRequest line = OrderLineRequest.builder()
                    .productId(1L)
                    .quantity(2)
                    .build();

            OrderRequest request = OrderRequest.builder()
                    .lines(List.of(line))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.create(request, "user@test.com"));
        }

        @Test
        @DisplayName("Création avec produit inexistant → exception")
        void create_unknownProduct_throwsException() {
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            OrderLineRequest line = OrderLineRequest.builder()
                    .productId(999L)
                    .quantity(1)
                    .build();

            OrderRequest request = OrderRequest.builder()
                    .lines(List.of(line))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.create(request, "user@test.com"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // Validation de commande — RG-02
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Validation de commande — RG-02")
    class ValidateOrderTests {

        @Test
        @DisplayName("Validation avec stock suffisant → succès + mouvement de sortie créé")
        void validate_sufficientStock() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-001")
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .build();

            var line = new com.stock.api.entity.OrderLine();
            line.setProduct(product);
            line.setQuantity(3);
            line.setUnitPrice(BigDecimal.valueOf(29.99));
            line.setSubtotal(BigDecimal.valueOf(89.97));
            order.setLines(List.of(line));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.validate(1L, "user@test.com");

            assertEquals(OrderStatus.VALIDATED, response.getStatus());
            verify(stockMovementRepository, times(1)).save(any());
            assertEquals(7, product.getQuantity(),
                    "RG-02 : la quantité doit être diminuée de 3 (10 - 3 = 7)");
        }

        @Test
        @DisplayName("Validation avec stock insuffisant → IllegalStateException")
        void validate_insufficientStock_throwsException() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-002")
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .build();

            var line = new com.stock.api.entity.OrderLine();
            line.setProduct(product);
            line.setQuantity(15); // Plus que les 10 disponibles
            line.setUnitPrice(BigDecimal.valueOf(29.99));
            line.setSubtotal(BigDecimal.valueOf(449.85));
            order.setLines(List.of(line));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> orderService.validate(1L, "user@test.com"));

            assertTrue(exception.getMessage().contains("Stock insuffisant"));
            verify(stockMovementRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("RG-02 : même produit dans 2 lignes, total > stock → rejet")
        void validate_sameProductTwoLines_exceedsStock_throwsException() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-RG02")
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .build();

            // 2 lignes pour le même produit (qty=10), chacune demande 6
            // Total = 12 > 10 disponibles → doit être rejeté
            // La ligne 1 passe (10 >= 6) mais la ligne 2 échoue (4 < 6)
            var line1 = new com.stock.api.entity.OrderLine();
            line1.setProduct(product);
            line1.setQuantity(6);
            line1.setUnitPrice(BigDecimal.valueOf(29.99));
            line1.setSubtotal(BigDecimal.valueOf(179.94));

            var line2 = new com.stock.api.entity.OrderLine();
            line2.setProduct(product); // Même instance JPA = même produit
            line2.setQuantity(6);
            line2.setUnitPrice(BigDecimal.valueOf(29.99));
            line2.setSubtotal(BigDecimal.valueOf(179.94));

            order.setLines(List.of(line1, line2));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> orderService.validate(1L, "user@test.com"));

            assertTrue(exception.getMessage().contains("Stock insuffisant"));
            assertTrue(exception.getMessage().contains("Produit Test"));
            // La ligne 1 a créé 1 mouvement, mais la commande n'est PAS validée
            verify(stockMovementRepository, times(1)).save(any());
            verify(orderRepository, never()).save(any());
            assertEquals(4, product.getQuantity(),
                    "Seule la ligne 1 a été traitée : 10 - 6 = 4");
        }

        @Test
        @DisplayName("RG-02 : même produit dans 2 lignes, total = stock → OK")
        void validate_sameProductTwoLines_exactStock_succeeds() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-RG02-OK")
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .build();

            // 2 lignes pour le même produit (qty=10), chacune demande 5
            // Total = 10 = 10 disponibles → doit être accepté
            var line1 = new com.stock.api.entity.OrderLine();
            line1.setProduct(product);
            line1.setQuantity(5);
            line1.setUnitPrice(BigDecimal.valueOf(29.99));
            line1.setSubtotal(BigDecimal.valueOf(149.95));

            var line2 = new com.stock.api.entity.OrderLine();
            line2.setProduct(product);
            line2.setQuantity(5);
            line2.setUnitPrice(BigDecimal.valueOf(29.99));
            line2.setSubtotal(BigDecimal.valueOf(149.95));

            order.setLines(List.of(line1, line2));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.validate(1L, "user@test.com");

            assertEquals(OrderStatus.VALIDATED, response.getStatus());
            verify(stockMovementRepository, times(2)).save(any());
            assertEquals(0, product.getQuantity(),
                    "Le stock doit être entièrement consommé (10 - 5 - 5 = 0)");
        }

        @Test
        @DisplayName("Validation d'une commande déjà validée → IllegalStateException")
        void validate_alreadyValidated_throwsException() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-003")
                    .status(OrderStatus.VALIDATED)
                    .createdBy(user)
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(IllegalStateException.class,
                    () -> orderService.validate(1L, "user@test.com"));

            verify(stockMovementRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════
    // Transitions de statut
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Transitions de statut")
    class StatusTransitionTests {

        @Test
        @DisplayName("Annulation d'une commande en attente → succès")
        void cancel_pendingOrder_success() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-004")
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancel(1L, "user@test.com");

            assertEquals(OrderStatus.CANCELLED, response.getStatus());
        }

        @Test
        @DisplayName("Annulation d'une commande validée par un profil de gestion → succès + restitution stock")
        void cancel_validatedOrder_success() {
            // L'annulation d'une commande déjà validée est réservée aux profils de gestion (V10-US).
            user.setRoles(Collections.singleton(Role.ADMIN));
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-005")
                    .status(OrderStatus.VALIDATED)
                    .createdBy(user)
                    .build();

            var line = new com.stock.api.entity.OrderLine();
            line.setProduct(product);
            line.setQuantity(5);
            line.setUnitPrice(BigDecimal.valueOf(29.99));
            line.setSubtotal(BigDecimal.valueOf(149.95));
            order.setLines(List.of(line));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancel(1L, "user@test.com");

            assertEquals(OrderStatus.CANCELLED, response.getStatus());
            verify(stockMovementRepository, times(1)).save(any());
            assertEquals(15, product.getQuantity(),
                    "Le stock doit être restitué (10 + 5 = 15)");
        }

        @Test
        @DisplayName("Annulation d'une commande déjà annulée → IllegalStateException")
        void cancel_alreadyCancelled_throwsException() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-006")
                    .status(OrderStatus.CANCELLED)
                    .createdBy(user)
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThrows(IllegalStateException.class,
                    () -> orderService.cancel(1L, "user@test.com"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // Entité Order — transitions autorisées
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Entité Order — canTransitionTo()")
    class OrderTransitionTests {

        @Test
        @DisplayName("PENDING → VALIDATED : autorisé")
        void pendingToValidated_allowed() {
            Order order = Order.builder().status(OrderStatus.PENDING).build();
            assertTrue(order.canTransitionTo(OrderStatus.VALIDATED));
        }

        @Test
        @DisplayName("PENDING → CANCELLED : autorisé")
        void pendingToCancelled_allowed() {
            Order order = Order.builder().status(OrderStatus.PENDING).build();
            assertTrue(order.canTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("VALIDATED → CANCELLED : autorisé (annulation avec restitution stock)")
        void validatedToCancelled_allowed() {
            Order order = Order.builder().status(OrderStatus.VALIDATED).build();
            assertFalse(order.canTransitionTo(OrderStatus.PENDING));
            assertTrue(order.canTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("CANCELLED →任何 : interdit")
        void cancelledToAny_forbidden() {
            Order order = Order.builder().status(OrderStatus.CANCELLED).build();
            assertFalse(order.canTransitionTo(OrderStatus.PENDING));
            assertFalse(order.canTransitionTo(OrderStatus.VALIDATED));
        }

        @Test
        @DisplayName("transitionTo() avec transition invalide (VALIDATED → PENDING) → IllegalStateException")
        void transitionTo_invalid_throwsException() {
            Order order = Order.builder().status(OrderStatus.VALIDATED).build();

            assertThrows(IllegalStateException.class,
                    () -> order.transitionTo(OrderStatus.PENDING));
        }
    }

    // ═══════════════════════════════════════════════════════
    // V10 — types de commande (PURCHASE / CLIENT)
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("V10 — commande fournisseur (PURCHASE) et commande client (CLIENT)")
    class OrderTypeTests {

        private User admin() {
            return User.builder()
                    .id(2L)
                    .email("admin@test.com")
                    .roles(Collections.singleton(Role.ADMIN))
                    .build();
        }

        private User seller() {
            return User.builder()
                    .id(3L)
                    .email("seller@test.com")
                    .roles(Collections.singleton(Role.SELLER))
                    .build();
        }

        @Test
        @DisplayName("Commande PURCHASE sans fournisseur → BusinessRuleException")
        void create_purchase_withoutSupplier_throwsException() {
            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.PURCHASE)
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(5).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> orderService.create(request, "admin@test.com"));
        }

        @Test
        @DisplayName("Commande PURCHASE par un vendeur seul → refusée")
        void create_purchase_bySeller_throwsException() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.PURCHASE)
                    .supplierId(10L)
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(5).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> orderService.create(request, "seller@test.com"));
        }

        @Test
        @DisplayName("Commande PURCHASE avec fournisseur valide → succès")
        void create_purchase_withSupplier_success() {
            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin()));
            Supplier supplier = Supplier.builder()
                    .id(10L)
                    .name("Distrib Plus")
                    .deleted(false)
                    .build();
            when(supplierRepository.findById(10L)).thenReturn(Optional.of(supplier));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(1L);
                return order;
            });

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.PURCHASE)
                    .supplierId(10L)
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(5).build()))
                    .build();

            OrderResponse response = orderService.create(request, "admin@test.com");

            assertEquals(OrderType.PURCHASE, response.getType());
            assertEquals(10L, response.getSupplierId());
            assertEquals("Distrib Plus", response.getSupplierName());
        }

        @Test
        @DisplayName("Commande CLIENT sans nom de client → BusinessRuleException")
        void create_client_withoutClientName_throwsException() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .deliveryDate(LocalDate.now().plusDays(2))
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> orderService.create(request, "seller@test.com"));
        }

        @Test
        @DisplayName("Commande CLIENT sans date de livraison → BusinessRuleException")
        void create_client_withoutDeliveryDate_throwsException() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("Client Test")
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> orderService.create(request, "seller@test.com"));
        }

        @Test
        @DisplayName("Commande CLIENT avec date de livraison dans le passé → BusinessRuleException")
        void create_client_pastDeliveryDate_throwsException() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("Client Test")
                    .deliveryDate(LocalDate.now().minusDays(1))
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> orderService.create(request, "seller@test.com"));
        }

        @Test
        @DisplayName("Commande CLIENT livrée aujourd'hui → acceptée")
        void create_client_todayDeliveryDate_accepted() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller()));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(1L);
                return order;
            });

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("Client Aujourd'hui")
                    .deliveryDate(LocalDate.now())
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            OrderResponse response = orderService.create(request, "seller@test.com");

            assertEquals(OrderStatus.PENDING, response.getStatus());
            assertEquals(LocalDate.now(), response.getDeliveryDate());
        }

        @Test
        @DisplayName("Commande CLIENT valide → PENDING avec client et date de livraison")
        void create_client_success() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller()));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order order = inv.getArgument(0);
                order.setId(1L);
                return order;
            });

            LocalDate delivery = LocalDate.now().plusDays(3);
            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("M. Koffi")
                    .deliveryDate(delivery)
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(2).build()))
                    .build();

            OrderResponse response = orderService.create(request, "seller@test.com");

            assertEquals(OrderStatus.PENDING, response.getStatus());
            assertEquals(OrderType.CLIENT, response.getType());
            assertEquals("M. Koffi", response.getClientName());
            assertEquals(delivery, response.getDeliveryDate());
        }

        @Test
        @DisplayName("Validation d'une commande PURCHASE → entrée de stock (quantité ajoutée)")
        void validate_purchase_addsStock() {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-PURCHASE")
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .orderType(OrderType.PURCHASE)
                    .build();

            var line = new com.stock.api.entity.OrderLine();
            line.setProduct(product);
            line.setQuantity(5);
            line.setUnitPrice(BigDecimal.valueOf(29.99));
            line.setSubtotal(BigDecimal.valueOf(149.95));
            order.setLines(List.of(line));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.validate(1L, "user@test.com");

            assertEquals(OrderStatus.VALIDATED, response.getStatus());
            assertEquals(15, product.getQuantity(),
                    "Une commande fournisseur validée ajoute la quantité au stock (10 + 5 = 15)");
        }

        @Test
        @DisplayName("Annulation d'une commande PURCHASE validée (profil de gestion) → stock retiré")
        void cancel_validatedPurchase_removesStock() {
            // L'annulation d'une commande déjà validée est réservée aux profils de gestion (V10-US).
            user.setRoles(Collections.singleton(Role.ADMIN));
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-PURCHASE-2")
                    .status(OrderStatus.VALIDATED)
                    .createdBy(user)
                    .orderType(OrderType.PURCHASE)
                    .build();

            var line = new com.stock.api.entity.OrderLine();
            line.setProduct(product);
            line.setQuantity(4);
            line.setUnitPrice(BigDecimal.valueOf(29.99));
            line.setSubtotal(BigDecimal.valueOf(119.96));
            order.setLines(List.of(line));

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancel(1L, "user@test.com");

            assertEquals(OrderStatus.CANCELLED, response.getStatus());
            assertEquals(6, product.getQuantity(),
                    "L'annulation retire les quantités entrées (10 - 4 = 6)");
        }
    }

    // ═══════════════════════════════════════════════════════
    // V10-US vendeur : modification et annulation de sa commande
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("V10-US — modification/annulation par le créateur (vendeur)")
    class SellerOrderTests {

        private User sellerCreator() {
            return User.builder()
                    .id(3L)
                    .email("seller@test.com")
                    .roles(Collections.singleton(Role.SELLER))
                    .build();
        }

        private User otherSeller() {
            return User.builder()
                    .id(4L)
                    .email("other@test.com")
                    .roles(Collections.singleton(Role.SELLER))
                    .build();
        }

        private Order pendingClientOrder(User creator) {
            Order order = Order.builder()
                    .id(1L)
                    .reference("CMD-CLIENT-1")
                    .status(OrderStatus.PENDING)
                    .createdBy(creator)
                    .orderType(OrderType.CLIENT)
                    .clientName("Client initial")
                    .deliveryDate(LocalDate.now().plusDays(5))
                    .companyId(1L)
                    .build();
            var line = new com.stock.api.entity.OrderLine();
            line.setProduct(product);
            line.setQuantity(2);
            line.setUnitPrice(BigDecimal.valueOf(29.99));
            line.setSubtotal(BigDecimal.valueOf(59.98));
            order.setLines(new java.util.ArrayList<>(List.of(line)));
            return order;
        }

        @Test
        @DisplayName("Le créateur modifie sa commande PENDING → client/date/lignes mises à jour")
        void update_byCreator_pending_success() {
            Order order = pendingClientOrder(sellerCreator());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(sellerCreator()));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("Nouveau client")
                    .deliveryDate(LocalDate.now().plusDays(10))
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(7).build()))
                    .notes("Note à jour")
                    .build();

            OrderResponse response = orderService.update(1L, request, "seller@test.com");

            assertEquals("Nouveau client", response.getClientName());
            assertEquals(LocalDate.now().plusDays(10), response.getDeliveryDate());
            assertEquals(1, response.getLines().size());
            assertEquals(7, response.getLines().get(0).getQuantity());
            assertEquals(0, product.getPrice().multiply(BigDecimal.valueOf(7)).compareTo(response.getTotalAmount()));
            assertEquals("Note à jour", response.getNotes());
        }

        @Test
        @DisplayName("Un vendeur qui n'est pas le créateur → BusinessRuleException")
        void update_byOtherSeller_throwsException() {
            Order order = pendingClientOrder(sellerCreator());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherSeller()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("X")
                    .deliveryDate(LocalDate.now().plusDays(1))
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> orderService.update(1L, request, "other@test.com"));
        }

        @Test
        @DisplayName("Un admin peut modifier la commande d'un autre vendeur (profil de gestion)")
        void update_byAdmin_otherSellerOrder_success() {
            Order order = pendingClientOrder(sellerCreator());
            User admin = User.builder()
                    .id(9L)
                    .email("admin@test.com")
                    .roles(Collections.singleton(Role.ADMIN))
                    .build();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("Client corrigé par admin")
                    .deliveryDate(LocalDate.now().plusDays(3))
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(4).build()))
                    .build();

            OrderResponse response = orderService.update(1L, request, "admin@test.com");

            assertEquals("Client corrigé par admin", response.getClientName());
            assertEquals(4, response.getLines().get(0).getQuantity());
        }

        @Test
        @DisplayName("Modification d'une commande VALIDATED → refusée")
        void update_validatedOrder_throwsException() {
            Order order = pendingClientOrder(sellerCreator());
            order.setStatus(OrderStatus.VALIDATED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(sellerCreator()));

            OrderRequest request = OrderRequest.builder()
                    .type(OrderType.CLIENT)
                    .clientName("X")
                    .deliveryDate(LocalDate.now().plusDays(1))
                    .lines(List.of(OrderLineRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> orderService.update(1L, request, "seller@test.com"));
        }

        @Test
        @DisplayName("Le créateur annule sa propre commande PENDING → CANCELLED sans mouvement")
        void cancel_byCreator_pending_success() {
            Order order = pendingClientOrder(sellerCreator());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(sellerCreator()));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancel(1L, "seller@test.com");

            assertEquals(OrderStatus.CANCELLED, response.getStatus());
            verify(stockMovementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Annulation d'une commande VALIDATED par un simple vendeur (non créateur) → refusée")
        void cancel_validated_byNonCreatorSeller_throwsException() {
            Order order = pendingClientOrder(sellerCreator());
            order.setStatus(OrderStatus.VALIDATED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherSeller()));

            assertThrows(BusinessRuleException.class,
                    () -> orderService.cancel(1L, "other@test.com"));
        }
    }
}
