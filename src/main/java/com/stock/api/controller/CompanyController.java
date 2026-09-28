package com.stock.api.controller;

import com.stock.api.dto.CompanyResponse;
import com.stock.api.entity.Company;
import com.stock.api.repository.CompanyRepository;
import com.stock.api.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administration des entreprises (multi-tenant).
 *
 * - GET /api/companies/me : l'entreprise de l'utilisateur connecté (utile à
 *   l'UI pour savoir si le module entrepôts est activé) ;
 * - GET /api/companies : toutes les entreprises (SUPER_ADMIN/ADMIN) ;
 * - PUT /api/companies/{id}/warehouse-module : active/désactive le module
 *   entrepôts d'une entreprise — remplace la manipulation SQL directe.
 *
 * Le module entrepôts étant OPTIONNEL, la désactivation est conservatrice :
 * les entrepôts existants sont conservés en base mais l'entreprise ne peut
 * plus en créer ni vendre depuis un entrepôt tant qu'il est désactivé.
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Entreprises", description = "Consultation et administration du module entrepôts par entreprise")
public class CompanyController {

    private final CompanyRepository companyRepository;

    @GetMapping("/me")
    @Operation(summary = "Entreprise de l'utilisateur connecté",
               description = "Retourne l'entreprise du token (warehouse_enabled indique si le module entrepôts est actif)")
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

    @PutMapping("/{id}/warehouse-module")
    @Operation(summary = "Activer/désactiver le module entrepôts",
               description = "Active ou désactive le module entrepôts pour une entreprise. " +
                             "La désactivation conserve les entrepôts existants (consultables) mais interdit " +
                             "les nouvelles créations et les ventes depuis un entrepôt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "État du module mis à jour"),
            @ApiResponse(responseCode = "404", description = "Entreprise non trouvée")
    })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<CompanyResponse> updateWarehouseModule(
            @PathVariable Long id,
            @RequestBody WarehouseModuleRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entreprise non trouvée avec l'id: " + id));
        company.setWarehouseEnabled(request.enabled());
        Company saved = companyRepository.save(company);
        return ResponseEntity.ok(toResponse(saved));
    }

    /** Corps de la requête PUT : { "enabled": true|false }. */
    public record WarehouseModuleRequest(boolean enabled) {}

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .active(company.isActive())
                .warehouseEnabled(company.isWarehouseEnabled())
                .userCount(0)
                .createdAt(company.getCreatedAt())
                .build();
    }
}
