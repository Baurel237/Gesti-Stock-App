package com.stock.api.service;

import com.stock.api.dto.WarehouseContentLineResponse;
import com.stock.api.dto.WarehouseResponse;
import com.stock.api.entity.Company;
import com.stock.api.entity.Product;
import com.stock.api.entity.Warehouse;
import com.stock.api.entity.WarehouseStock;
import com.stock.api.repository.CompanyRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.repository.WarehouseRepository;
import com.stock.api.repository.WarehouseStockRepository;
import com.stock.api.service.AuditService;
import com.stock.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour WarehouseService — contenu et totaux d'un entrepôt.
 * Point clé : les produits soft-supprimés (RG-04) ne doivent plus apparaître
 * dans GET /api/warehouses/{id}/content, ni compter dans totalQuantity, ni
 * empêcher la suppression d'un entrepôt vide — même si leurs lignes de stock
 * par entrepôt subsistent en base.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseStockRepository warehouseStockRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private WarehouseScopeService warehouseScopeService;

    @InjectMocks
    private WarehouseService warehouseService;

    private static final long COMPANY_ID = 10L;

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(COMPANY_ID);
        warehouse = Warehouse.builder()
                .id(1L)
                .company(Company.builder().id(COMPANY_ID).build())
                .name("Entrepôt principal")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getContent : exclut les produits soft-supprimés même avec du stock restant")
    void getContentExcludesSoftDeletedProducts() {
        Product active = Product.builder().id(100L).name("Clavier sans fil").reference("KB-1")
                .price(new BigDecimal("49.99")).deleted(false).build();
        Product deleted = Product.builder().id(200L).name("Ancien produit").reference("OLD-1")
                .price(BigDecimal.TEN).deleted(true).build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        // La requête dérivée exclut les produits soft-supprimés : seule la
        // ligne active est retournée, même si la ligne du produit supprimé
        // subsiste en base.
        when(warehouseStockRepository.findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(1L))
                .thenReturn(List.of(
                        WarehouseStock.builder().id(11L).companyId(COMPANY_ID).product(active)
                                .warehouse(warehouse).quantity(7).build()
                ));

        List<WarehouseContentLineResponse> content = warehouseService.getContent(1L);

        assertEquals(1, content.size());
        WarehouseContentLineResponse line = content.get(0);
        assertEquals(100L, line.getProductId().longValue());
        assertEquals(7, line.getQuantity().intValue());
        verify(warehouseStockRepository, never())
                .findByWarehouseIdOrderByProductId(1L);
    }

    @Test
    @DisplayName("getContent : exclut les lignes à quantité nulle")
    void getContentExcludesZeroQuantityLines() {
        Product active = Product.builder().id(100L).name("Clavier sans fil").reference("KB-1")
                .price(new BigDecimal("49.99")).deleted(false).build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseStockRepository.findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(1L))
                .thenReturn(List.of(
                        WarehouseStock.builder().id(11L).companyId(COMPANY_ID).product(active)
                                .warehouse(warehouse).quantity(0).build()
                ));

        List<WarehouseContentLineResponse> content = warehouseService.getContent(1L);

        assertTrue(content.isEmpty());
    }

    @Test
    @DisplayName("findById : totalQuantity somme uniquement le stock des produits actifs")
    void findByIdTotalExcludesSoftDeletedProducts() {
        Product active = Product.builder().id(100L).name("Clavier sans fil").reference("KB-1")
                .price(new BigDecimal("49.99")).deleted(false).build();
        Product deleted = Product.builder().id(200L).name("Ancien produit").reference("OLD-1")
                .price(BigDecimal.TEN).deleted(true).build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        // La requête dérivée exclut déjà les produits soft-supprimés : seule
        // la ligne active est retournée, le total doit la refléter (7, pas 11).
        when(warehouseStockRepository.findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(1L))
                .thenReturn(List.of(
                        WarehouseStock.builder().id(11L).companyId(COMPANY_ID).product(active)
                                .warehouse(warehouse).quantity(7).build()
                ));

        WarehouseResponse response = warehouseService.findById(1L);

        assertEquals(7, response.getTotalQuantity());
        verify(warehouseStockRepository)
                .findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(1L);
        verify(warehouseStockRepository, never())
                .findByWarehouseIdOrderByProductId(1L);
    }

    @Test
    @DisplayName("delete : un entrepôt ne contenant que du stock de produits supprimés est supprimable")
    void deleteAllowsWarehouseWithOnlySoftDeletedProductStock() {
        Product deleted = Product.builder().id(200L).name("Ancien produit").reference("OLD-1")
                .price(BigDecimal.TEN).deleted(true).build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        // Le contenu visible est vide : la ligne du produit supprimé est
        // ignorée par la requête filtrée, l'entrepôt est donc supprimable.
        when(warehouseStockRepository.findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(1L))
                .thenReturn(List.of());

        warehouseService.delete(1L);

        verify(warehouseStockRepository).deleteByWarehouseId(1L);
        verify(warehouseRepository).delete(warehouse);
    }
}
