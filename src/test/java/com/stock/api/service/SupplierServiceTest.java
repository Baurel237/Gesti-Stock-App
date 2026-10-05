package com.stock.api.service;

import com.stock.api.entity.Supplier;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour SupplierService — story 2.3 : la suppression
 * logique d'un fournisseur retire ses associations aux produits
 * (product_suppliers) sans toucher aux mouvements passés (story 2.2).
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SupplierService supplierService;

    private Supplier supplier;

    @BeforeEach
    void setUp() {
        supplier = Supplier.builder()
                .id(10L)
                .name("Fournisseur A")
                .companyId(1L)
                .build();
    }

    @Test
    @DisplayName("delete retire le fournisseur de tous les produits (unlinkSupplier)")
    void deleteAvecUnlink() {
        when(supplierRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(supplier));

        supplierService.delete(10L);

        verify(supplierRepository).save(argThat(s -> s.isDeleted()));
        verify(productRepository).unlinkSupplier(10L);
    }

    @Test
    @DisplayName("delete d'un fournisseur inconnu ne touche pas aux produits")
    void deleteInconnu() {
        when(supplierRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> supplierService.delete(99L));

        verify(productRepository, never()).unlinkSupplier(any());
    }
}
