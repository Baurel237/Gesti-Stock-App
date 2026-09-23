package com.stock.api.service;

import com.stock.api.dto.CompanyRequest;
import com.stock.api.dto.CompanyResponse;
import com.stock.api.entity.Company;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.CompanyRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Gestion des entreprises — réservée au SUPER_ADMIN (portée plateforme).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<CompanyResponse> findAll(Pageable pageable) {
        return companyRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse findById(Long id) {
        return companyRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Entreprise non trouvée avec l'id: " + id));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        String slug = normalizeSlug(request.getSlug() != null ? request.getSlug() : request.getName());
        if (companyRepository.existsBySlug(slug)) {
            throw new BusinessRuleException("Une entreprise utilise déjà l'identifiant « " + slug + " »");
        }

        Company company = companyRepository.save(Company.builder()
                .name(request.getName().trim())
                .slug(slug)
                .active(true)
                .warehouseEnabled(Boolean.TRUE.equals(request.getWarehouseEnabled()))
                .build());

        auditService.recordFor(currentUserEmail(), "SUPER_ADMIN", "CREATE", "Company",
                company.getId(), company.getName(), "Entreprise créée");
        return toResponse(company);
    }

    /**
     * Activation / suspension. Une entreprise suspendue garde ses données mais
     * ses utilisateurs ne peuvent plus se connecter (vérifié au login).
     */
    @Transactional
    public CompanyResponse setActive(Long id, boolean active) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entreprise non trouvée avec l'id: " + id));
        company.setActive(active);
        Company saved = companyRepository.save(company);
        auditService.recordFor(currentUserEmail(), "SUPER_ADMIN", active ? "ACTIVATE" : "SUSPEND",
                "Company", saved.getId(), saved.getName(),
                active ? "Entreprise réactivée" : "Entreprise suspendue");
        return toResponse(saved);
    }

    private String normalizeSlug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            throw new BusinessRuleException("Identifiant d'entreprise invalide");
        }
        return slug;
    }

    private String currentUserEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private CompanyResponse toResponse(Company company) {
        long userCount = TenantContext.getCompanyId() == null
                ? userRepository.findByCompanyId(company.getId()).size()
                : 0;
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .active(company.isActive())
                .warehouseEnabled(company.isWarehouseEnabled())
                .userCount(userCount)
                .createdAt(company.getCreatedAt())
                .build();
    }
}
