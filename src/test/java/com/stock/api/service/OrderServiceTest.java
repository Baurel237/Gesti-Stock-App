package com.stock.api.service;

import com.stock.api.dto.OrderLineRequest;
import com.stock.api.dto.OrderRequest;
import com.stock.api.dto.OrderResponse;
import com.stock.api.entity.Order;
import com.stock.api.entity.Order.OrderStatus;
import com.stock.api.entity.Product;
import com.stock.api.entity.User;
import com.stock.api.repository.OrderRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
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
        @DisplayName("Annulation d'une commande validée → succès + restitution stock")
        void cancel_validatedOrder_success() {
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
}
