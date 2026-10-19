package com.stock.api.controller;

import com.stock.api.dto.CompanyResponse;
import com.stock.api.entity.Company;
import com.stock.api.repository.CompanyRepository;
import com.stock.api.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consultation des entreprises (multi-tenant).
 *
 * - GET /api/companies/me : l'entreprise de l'utilisateur connecté ;
 * - GET /api/companies : toutes les entreprises (SUPER_ADMIN/ADMIN).
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Entreprises", description = "Consultation des entreprises")
public class CompanyController {

    private final CompanyRepository companyRepository;

    @GetMapping("/me")
    @Operation(summary = "Entreprise de l'utilisateur connecté")
    public ResponseEntity<CompanyResponse> findMine() {
        Long companyId = TenantGuard.requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Entreprise introuvable pour l'utilisateur connecté"));
        return ResponseEntity.ok(toResponse(company));
    }

    @GetMapping
    @Operation(summary = "Lister toutes les entreprises",
               description = "Réservé à l'administration (SUPER_ADMIN / ADMIN)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<CompanyResponse>> findAll() {
        return ResponseEntity.ok(companyRepository.findAll().stream()
                .map(this::toResponse)
                .toList());
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .active(company.isActive())
                .userCount(0)
                .createdAt(company.getCreatedAt())
                .build();
    }
}
