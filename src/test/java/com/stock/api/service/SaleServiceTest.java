package com.stock.api.service;

import com.stock.api.dto.SaleItemRequest;
import com.stock.api.dto.SaleRequest;
import com.stock.api.dto.SaleResponse;
import com.stock.api.entity.Product;
import com.stock.api.entity.Role;
import com.stock.api.entity.Sale;
import com.stock.api.entity.SaleItem;
import com.stock.api.entity.User;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SaleRepository;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour SaleService.
 * Couvre notamment le calcul du sous-total : SaleItem.subtotal n'est
 * plus seulement calculé en @PrePersist mais bien avant calculateTotal()
 * (sinon NPE à la création de la vente).
 */
@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SaleService saleService;

    private User seller;
    private User admin;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = User.builder()
                .id(1L)
                .email("vendeur@test.com")
                .firstName("Vendeur")
                .lastName("Test")
                .roles(java.util.Set.of(Role.SELLER))
                .build();

        admin = User.builder()
                .id(2L)
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("Test")
                .roles(java.util.Set.of(Role.ADMIN))
                .build();

        product = Product.builder()
                .id(1L)
                .name("Clavier sans fil")
                .reference("KB-BT-001")
                .price(new BigDecimal("49.99"))
                .quantity(10)
                .alertThreshold(5)
                .deleted(false)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // Calcul du sous-total et du montant total
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Calcul du sous-total (fix NPE calculateTotal)")
    class SubtotalCalculationTests {

        @Test
        @DisplayName("Créer une vente → sous-totaux et total cohérents")
        void create_computesSubtotalsBeforeCalculateTotal() {
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> {
                Sale sale = inv.getArgument(0);
                sale.setId(100L);
                return sale;
            });

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder()
                            .productId(1L)
                            .quantity(3)
                            .build()))
                    .notes("Test sous-total")
                    .build();

            SaleResponse response = saleService.create(request, "vendeur@test.com");

            assertNotNull(response);
            // 3 × 49.99 = 149.97
            assertEquals(0, new BigDecimal("149.97").compareTo(response.getTotalAmount()),
                    "Le total doit être 149.97 (3 × 49.99)");
            assertEquals(3, response.getTotalItems());

            // Le sous-total doit être présent AVANT calculateTotal()
            // (sinon NPE : @PrePersist ne s'exécute qu'à l'insertion)
            verify(saleRepository).save(any(Sale.class));
            assertNotNull(response.getItems().get(0).getSubtotal(),
                    "Le sous-total de la ligne doit être calculé");
            assertEquals(0, new BigDecimal("149.97").compareTo(response.getItems().get(0).getSubtotal()));
        }

        @Test
        @DisplayName("Vente multi-lignes → total = somme des sous-totaux")
        void create_multipleLines_totalIsSumOfSubtotals() {
            Product screen = Product.builder()
                    .id(2L)
                    .name("Écran 24 pouces")
                    .reference("ECR-24-001")
                    .price(new BigDecimal("149.00"))
                    .quantity(5)
                    .alertThreshold(2)
                    .deleted(false)
                    .build();

            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.findById(2L)).thenReturn(Optional.of(screen));
            when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(
                            SaleItemRequest.builder().productId(1L).quantity(1).build(),
                            SaleItemRequest.builder().productId(2L).quantity(2).build()))
                    .build();

            SaleResponse response = saleService.create(request, "vendeur@test.com");

            // 1 × 49.99 + 2 × 149.00 = 347.99
            assertEquals(0, new BigDecimal("347.99").compareTo(response.getTotalAmount()));
            assertEquals(3, response.getTotalItems());
        }

        @Test
        @DisplayName("Vente avec quantité 1 → sous-total = prix unitaire")
        void create_singleUnit_subtotalEqualsUnitPrice() {
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            SaleResponse response = saleService.create(request, "vendeur@test.com");

            assertEquals(0, new BigDecimal("49.99").compareTo(response.getItems().get(0).getSubtotal()));
            assertEquals(0, new BigDecimal("49.99").compareTo(response.getItems().get(0).getUnitPrice()));
        }
    }

    // ═══════════════════════════════════════════════════════
    // RG-02 : Décrément du stock
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("RG-02 — Décrément du stock à la vente")
    class StockDecrementTests {

        @Test
        @DisplayName("Vente → stock décrémenté de la quantité vendue")
        void create_decrementsStock() {
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder().productId(1L).quantity(4).build()))
                    .build();

            saleService.create(request, "vendeur@test.com");

            assertEquals(6, product.getQuantity(), "Le stock doit être décrémenté de 4");
            verify(productRepository).save(product);
        }
    }

    // ═══════════════════════════════════════════════════════
    // Cas d'erreur
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Cas d'erreur")
    class ErrorCasesTests {

        @Test
        @DisplayName("Stock insuffisant → BusinessRuleException, vente non sauvegardée")
        void create_insufficientStock_throwsAndDoesNotSave() {
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder().productId(1L).quantity(999).build()))
                    .build();

            BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                    () -> saleService.create(request, "vendeur@test.com"));

            assertTrue(exception.getMessage().contains("Stock insuffisant"));
            assertTrue(exception.getMessage().contains("Disponible : 10"));
            assertTrue(exception.getMessage().contains("demandé : 999"));
            verify(saleRepository, never()).save(any(Sale.class));
        }

        @Test
        @DisplayName("Vente sans articles → BusinessRuleException")
        void create_noItems_throws() {
            // La validation des articles se fait AVANT la recherche du vendeur :
            // aucun stub n'est nécessaire ici.

            SaleRequest request = SaleRequest.builder()
                    .items(List.of())
                    .build();

            BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                    () -> saleService.create(request, "vendeur@test.com"));

            assertTrue(exception.getMessage().contains("au moins un article"));
            verify(saleRepository, never()).save(any(Sale.class));
        }

        @Test
        @DisplayName("Produit supprimé → BusinessRuleException")
        void create_deletedProduct_throws() {
            product.setDeleted(true);
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> saleService.create(request, "vendeur@test.com"));
            verify(saleRepository, never()).save(any(Sale.class));
        }

        @Test
        @DisplayName("Produit inexistant → BusinessRuleException")
        void create_productNotFound_throws() {
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder().productId(999L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> saleService.create(request, "vendeur@test.com"));
        }

        @Test
        @DisplayName("Vendeur inexistant → BusinessRuleException")
        void create_sellerNotFound_throws() {
            when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(SaleItemRequest.builder().productId(1L).quantity(1).build()))
                    .build();

            assertThrows(BusinessRuleException.class,
                    () -> saleService.create(request, "unknown@test.com"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // Annulation : fenêtre 4 h + demande admin
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Annulation — auto (4 h) et demande admin")
    class CancellationTests {

        private Sale completedSale(int minutesAgo) {
            Sale sale = Sale.builder()
                    .id(50L)
                    .reference("VTE-TEST01")
                    .seller(seller)
                    .status(Sale.SaleStatus.COMPLETED)
                    .build();
            SaleItem item = SaleItem.builder()
                    .id(60L)
                    .product(product)
                    .quantity(3)
                    .unitPrice(product.getPrice())
                    .subtotal(product.getPrice().multiply(BigDecimal.valueOf(3)))
                    .build();
            sale.addItem(item);
            sale.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(minutesAgo));
            return sale;
        }

        @SuppressWarnings("UnnecessaryStubbing")
        private void mockSaleAndUsers(Sale sale) {
            lenient().when(saleRepository.findById(50L)).thenReturn(Optional.of(sale));
            lenient().when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));
            lenient().when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
            lenient().when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Auto-annulation dans les 4 heures → OK, stock remis")
        void cancel_bySellerWithinWindow_restocks() {
            Sale sale = completedSale(2); // 2 minutes : bien dans la fenêtre de 4 h
            mockSaleAndUsers(sale);
            int qtyBefore = product.getQuantity();

            SaleResponse response = saleService.cancel(50L, "vendeur@test.com");

            assertEquals(Sale.SaleStatus.CANCELLED, response.getStatus());
            assertEquals(qtyBefore + 3, product.getQuantity(), "Les 3 unités doivent être remises en stock");
            verify(stockMovementRepository).save(any());
            assertFalse(sale.isCancellationRequested());
        }

        @Test
        @DisplayName("Auto-annulation après 4 heures → BusinessRuleException")
        void cancel_bySellerAfterWindow_throws() {
            Sale sale = completedSale(5 * 60); // 5 heures : au-delà de la fenêtre de 4 h
            mockSaleAndUsers(sale);

            BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                    () -> saleService.cancel(50L, "vendeur@test.com"));

            assertTrue(exception.getMessage().contains("4"));
            assertEquals(Sale.SaleStatus.COMPLETED, sale.getStatus());
            verify(saleRepository, never()).save(any(Sale.class));
        }

        @Test
        @DisplayName("Admin peut annuler même après 4 heures → OK")
        void cancel_byAdminAfterWindow_ok() {
            Sale sale = completedSale(5 * 60); // 5 heures
            mockSaleAndUsers(sale);

            SaleResponse response = saleService.cancel(50L, "admin@test.com");

            assertEquals(Sale.SaleStatus.CANCELLED, response.getStatus());
            assertEquals(product.getQuantity(), 10 + 3);
        }

        @Test
        @DisplayName("Vendeur ne peut pas annuler la vente d'un autre → AccessDeniedException")
        void cancel_otherSellerSale_throws() {
            Sale sale = completedSale(2);
            User otherSeller = User.builder()
                    .id(9L)
                    .email("autre@test.com")
                    .firstName("Autre")
                    .lastName("Vendeur")
                    .roles(java.util.Set.of(Role.SELLER))
                    .build();
            sale.setSeller(otherSeller);
            when(saleRepository.findById(50L)).thenReturn(Optional.of(sale));
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));

            assertThrows(AccessDeniedException.class,
                    () -> saleService.cancel(50L, "vendeur@test.com"));
        }

        @Test
        @DisplayName("Vente déjà annulée → BusinessRuleException")
        void cancel_alreadyCancelled_throws() {
            Sale sale = completedSale(2);
            sale.setStatus(Sale.SaleStatus.CANCELLED);
            mockSaleAndUsers(sale);

            assertThrows(BusinessRuleException.class,
                    () -> saleService.cancel(50L, "vendeur@test.com"));
        }

        @Test
        @DisplayName("Demande d'annulation après 4 heures → enregistrée")
        void requestCancellation_afterWindow_saved() {
            Sale sale = completedSale(5 * 60); // 5 heures
            mockSaleAndUsers(sale);

            SaleResponse response = saleService.requestCancellation(50L, "vendeur@test.com", "Erreur de saisie");

            assertTrue(response.isCancellationRequested());
            assertEquals("Erreur de saisie", response.getCancellationReason());
            assertNotNull(sale.getCancellationRequestedAt());
        }

        @Test
        @DisplayName("Demande d'annulation pendant les 4 heures → refusée (auto-annulation possible)")
        void requestCancellation_withinWindow_throws() {
            Sale sale = completedSale(2); // 2 minutes
            mockSaleAndUsers(sale);

            assertThrows(BusinessRuleException.class,
                    () -> saleService.requestCancellation(50L, "vendeur@test.com", null));
        }

        @Test
        @DisplayName("Demande d'annulation par un autre vendeur → AccessDeniedException")
        void requestCancellation_otherSeller_throws() {
            Sale sale = completedSale(5 * 60);
            User otherSeller = User.builder()
                    .id(9L)
                    .email("autre@test.com")
                    .firstName("Autre")
                    .lastName("Vendeur")
                    .roles(java.util.Set.of(Role.SELLER))
                    .build();
            sale.setSeller(otherSeller);
            when(saleRepository.findById(50L)).thenReturn(Optional.of(sale));
            when(userRepository.findByEmail("vendeur@test.com")).thenReturn(Optional.of(seller));

            assertThrows(AccessDeniedException.class,
                    () -> saleService.requestCancellation(50L, "vendeur@test.com", null));
        }

        @Test
        @DisplayName("Demande en double → BusinessRuleException")
        void requestCancellation_duplicate_throws() {
            Sale sale = completedSale(5 * 60);
            sale.setCancellationRequested(true);
            mockSaleAndUsers(sale);

            assertThrows(BusinessRuleException.class,
                    () -> saleService.requestCancellation(50L, "vendeur@test.com", null));
        }
    }
}
