package com.stock.api.service;

import com.stock.api.dto.ProductRequest;
import com.stock.api.dto.ProductResponse;
import com.stock.api.entity.Category;
import com.stock.api.entity.Product;
import com.stock.api.entity.Supplier;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SupplierRepository;
import com.stock.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour ProductService — stories 2.3/2.4.
 * 2.3 : association produits ↔ fournisseurs (validation entreprise/état).
 * 2.4 : filtre du catalogue par fournisseur avec isolation tenant.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private com.stock.api.repository.UserRepository userRepository;

    @Mock
    private WarehouseScopeService warehouseScopeService;

    @InjectMocks
    private ProductService productService;

    private static final Long COMPANY_ID = 1L;

    private Category category;
    private Product product;
    private Supplier supplierA;
    private Supplier supplierB;

    @BeforeEach
    void setUp() {
        // Le service lit la portée du jeton via TenantContext (ThreadLocal).
        TenantContext.setCompanyId(COMPANY_ID);
        category = Category.builder()
                .id(5L)
                .name("Catégorie")
                .companyId(COMPANY_ID)
                .build();
        supplierA = Supplier.builder()
                .id(10L)
                .name("Fournisseur A")
                .companyId(COMPANY_ID)
                .build();
        supplierB = Supplier.builder()
                .id(20L)
                .name("Fournisseur B")
                .companyId(COMPANY_ID)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ProductRequest baseRequest() {
        return ProductRequest.builder()
                .name("Produit Test")
                .reference("REF-001")
                .price(new BigDecimal("9.99"))
                .categoryId(5L)
                .quantity(0)
                .alertThreshold(10)
                .build();
    }

    @Nested
    @DisplayName("Story 2.3 : association produits ↔ fournisseurs")
    class AssociationFournisseurs {

        @Test
        @DisplayName("create avec supplierIds valides rattache les fournisseurs")
        void createAvecFournisseurs() {
            ProductRequest request = baseRequest().toBuilder()
                    .supplierIds(List.of(10L, 20L))
                    .build();
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(supplierRepository.findById(10L)).thenReturn(Optional.of(supplierA));
            when(supplierRepository.findById(20L)).thenReturn(Optional.of(supplierB));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.create(request);

            assertEquals(List.of(10L, 20L), response.getSupplierIds());
        }

        @Test
        @DisplayName("create sans supplierIds produit un produit sans fournisseur")
        void createSansFournisseur() {
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.create(baseRequest());

            assertNotNull(response.getSupplierIds());
            assertTrue(response.getSupplierIds().isEmpty());
            verifyNoInteractions(supplierRepository);
        }

        @Test
        @DisplayName("create avec un fournisseur inconnu est rejeté")
        void createFournisseurInconnu() {
            ProductRequest request = baseRequest().toBuilder()
                    .supplierIds(List.of(99L))
                    .build();
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class, () -> productService.create(request));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("create avec un fournisseur supprimé logiquement est rejeté")
        void createFournisseurSupprime() {
            Supplier supprime = Supplier.builder()
                    .id(30L)
                    .name("Fournisseur supprimé")
                    .companyId(COMPANY_ID)
                    .deleted(true)
                    .build();
            ProductRequest request = baseRequest().toBuilder()
                    .supplierIds(List.of(30L))
                    .build();
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(supplierRepository.findById(30L)).thenReturn(Optional.of(supprime));

            assertThrows(BusinessRuleException.class, () -> productService.create(request));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("create avec un fournisseur d'une autre société est rejeté")
        void createFournisseurAutreSociete() {
            Supplier etranger = Supplier.builder()
                    .id(40L)
                    .name("Fournisseur étranger")
                    .companyId(999L)
                    .build();
            ProductRequest request = baseRequest().toBuilder()
                    .supplierIds(List.of(40L))
                    .build();
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(supplierRepository.findById(40L)).thenReturn(Optional.of(etranger));

            assertThrows(BusinessRuleException.class, () -> productService.create(request));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("update sans supplierIds conserve les fournisseurs existants")
        void updateSansSupplierIdsConserve() {
            product = Product.builder()
                    .id(1L)
                    .name("Produit Test")
                    .reference("REF-001")
                    .price(new BigDecimal("9.99"))
                    .category(category)
                    .companyId(COMPANY_ID)
                    .build();
            product.getSuppliers().add(supplierA);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.update(1L, baseRequest());

            assertEquals(List.of(10L), response.getSupplierIds());
        }

        @Test
        @DisplayName("update avec une liste vide retire tous les fournisseurs")
        void updateListeVideRetire() {
            product = Product.builder()
                    .id(1L)
                    .name("Produit Test")
                    .reference("REF-001")
                    .price(new BigDecimal("9.99"))
                    .category(category)
                    .companyId(COMPANY_ID)
                    .build();
            product.getSuppliers().add(supplierA);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.update(1L, baseRequest().toBuilder()
                    .supplierIds(List.of())
                    .build());

            assertTrue(response.getSupplierIds().isEmpty());
        }

        @Test
        @DisplayName("update remplace les fournisseurs existants par la nouvelle liste")
        void updateRemplace() {
            product = Product.builder()
                    .id(1L)
                    .name("Produit Test")
                    .reference("REF-001")
                    .price(new BigDecimal("9.99"))
                    .category(category)
                    .companyId(COMPANY_ID)
                    .build();
            product.getSuppliers().add(supplierA);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
            when(supplierRepository.findById(20L)).thenReturn(Optional.of(supplierB));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.update(1L, baseRequest().toBuilder()
                    .supplierIds(List.of(20L))
                    .build());

            assertEquals(List.of(20L), response.getSupplierIds());
        }
    }

    @Nested
    @DisplayName("Story 2.4 : filtre du catalogue par fournisseur")
    class FiltreParFournisseur {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("findAll sans supplierId ne consulte pas le référentiel fournisseurs")
        void findAllSansFiltre() {
            when(productRepository.findByFilters(eq(COMPANY_ID), isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            productService.findAll(null, null, null, pageable);

            verifyNoInteractions(supplierRepository);
        }

        @Test
        @DisplayName("findAll avec un supplierId valide passe le filtre au repository")
        void findAllAvecFiltreValide() {
            when(supplierRepository.findById(10L)).thenReturn(Optional.of(supplierA));
            when(productRepository.findByFilters(eq(COMPANY_ID), isNull(), eq(10L), isNull(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<ProductResponse> page = productService.findAll(null, null, 10L, pageable);

            assertNotNull(page);
            verify(productRepository).findByFilters(eq(COMPANY_ID), isNull(), eq(10L), isNull(), eq(pageable));
        }

        @Test
        @DisplayName("findAll avec un fournisseur inconnu est rejeté (400, pas de fuite)")
        void findAllFournisseurInconnu() {
            when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessRuleException.class,
                    () -> productService.findAll(null, null, 99L, pageable));
            verify(productRepository, never()).findByFilters(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("findAll avec un fournisseur supprimé est rejeté")
        void findAllFournisseurSupprime() {
            Supplier supprime = Supplier.builder()
                    .id(30L)
                    .name("Fournisseur supprimé")
                    .companyId(COMPANY_ID)
                    .deleted(true)
                    .build();
            when(supplierRepository.findById(30L)).thenReturn(Optional.of(supprime));

            assertThrows(BusinessRuleException.class,
                    () -> productService.findAll(null, null, 30L, pageable));
            verify(productRepository, never()).findByFilters(any(), any(), any(), any(), any());
        }
    }
}
