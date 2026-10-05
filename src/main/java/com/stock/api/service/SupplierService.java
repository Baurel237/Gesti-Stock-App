package com.stock.api.service;

import com.stock.api.dto.SupplierRequest;
import com.stock.api.dto.SupplierResponse;
import com.stock.api.entity.Supplier;
import com.stock.api.repository.SupplierRepository;
import com.stock.api.tenant.TenantContext;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des fournisseurs (story 2.1).
 * RG-04 : suppression logique, comme les produits et catégories.
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditService auditService;

    /**
     * Liste paginée des fournisseurs non supprimés, avec recherche
     * facultative par nom (?search=...).
     */
    @Transactional(readOnly = true)
    public Page<SupplierResponse> findAll(String search, Pageable pageable) {
        return supplierRepository.findByFilters(TenantContext.getCompanyId(), search, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(Long id) {
        Supplier supplier = getActiveSupplier(id);
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Long companyId = TenantGuard.requireCompanyId();
        String name = request.getName().trim();
        if (supplierRepository.existsByCompanyIdAndNameAndDeletedFalse(companyId, name)) {
            throw new IllegalStateException("Un fournisseur avec ce nom existe déjà");
        }

        Supplier supplier = Supplier.builder()
                .name(name)
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .companyId(companyId)
                .build();

        supplier = supplierRepository.save(supplier);
        auditService.record("CREATE", "Supplier", supplier.getId(), supplier.getName(),
                "Fournisseur créé");
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = getActiveSupplier(id);

        String name = request.getName().trim();
        if (!supplier.getName().equals(name)
                && supplierRepository.existsByCompanyIdAndNameAndDeletedFalse(
                        supplier.getCompanyId(), name)) {
            throw new IllegalStateException("Un fournisseur avec ce nom existe déjà");
        }

        supplier.setName(name);
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setAddress(request.getAddress());

        supplier = supplierRepository.save(supplier);
        auditService.record("UPDATE", "Supplier", supplier.getId(), supplier.getName(),
                "Fournisseur mis à jour");
        return toResponse(supplier);
    }

    /**
     * Suppression logique (RG-04) : le fournisseur reste en base pour
     * l'historique mais disparaît des listes et des sélecteurs.
     */
    @Transactional
    public void delete(Long id) {
        Supplier supplier = getActiveSupplier(id);

        supplier.setDeleted(true);
        supplierRepository.save(supplier);
        auditService.record("DELETE", "Supplier", supplier.getId(), supplier.getName(),
                "Fournisseur supprimé (suppression logique)");
    }

    private Supplier getActiveSupplier(Long id) {
        Supplier supplier = supplierRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Fournisseur non trouvé avec l'id: " + id));
        TenantGuard.assertSameCompany(supplier.getCompanyId());
        return supplier;
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .address(supplier.getAddress())
                .deleted(supplier.isDeleted())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}
