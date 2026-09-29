package com.stock.api.service;

import com.stock.api.dto.WarehouseContentLineResponse;
import com.stock.api.entity.Company;
import com.stock.api.entity.Product;
import com.stock.api.entity.Warehouse;
import com.stock.api.entity.WarehouseStock;
import com.stock.api.repository.WarehouseRepository;
import com.stock.api.repository.WarehouseStockRepository;
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
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour WarehouseService — contenu d'un entrepôt.
 * Point clé : les produits soft-supprimés (RG-04) ne doivent plus
 * apparaître dans GET /api/warehouses/{id}/content, même si leurs
 * lignes de stock par entrepôt subsistent en base.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseStockRepository warehouseStockRepository;

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
        when(warehouseStockRepository.findByWarehouseIdOrderByProductId(1L)).thenReturn(List.of(
                WarehouseStock.builder().id(11L).companyId(COMPANY_ID).product(active)
                        .warehouse(warehouse).quantity(7).build(),
                WarehouseStock.builder().id(12L).companyId(COMPANY_ID).product(deleted)
                        .warehouse(warehouse).quantity(4).build()
        ));

        List<WarehouseContentLineResponse> content = warehouseService.getContent(1L);

        assertEquals(1, content.size());
        WarehouseContentLineResponse line = content.get(0);
        assertEquals(100L, line.getProductId().longValue());
        assertEquals(7, line.getQuantity().intValue());
    }

    @Test
    @DisplayName("getContent : exclut les lignes à quantité nulle")
    void getContentExcludesZeroQuantityLines() {
        Product active = Product.builder().id(100L).name("Clavier sans fil").reference("KB-1")
                .price(new BigDecimal("49.99")).deleted(false).build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseStockRepository.findByWarehouseIdOrderByProductId(1L)).thenReturn(List.of(
                WarehouseStock.builder().id(11L).companyId(COMPANY_ID).product(active)
                        .warehouse(warehouse).quantity(0).build()
        ));

        List<WarehouseContentLineResponse> content = warehouseService.getContent(1L);

        assertTrue(content.isEmpty());
    }
}
