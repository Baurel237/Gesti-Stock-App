package com.stock.api.service;

import com.stock.api.dto.AppSettingsResponse;
import com.stock.api.entity.AppSettings;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Paramètres globaux de l'application.
 * Le singleton (id = 1) est créé au premier accès avec la devise EUR.
 *
 * Devise : le code est stocké en base et lu par le front-end pour formater
 * tous les montants (dashboard, ventes, produits, reçus).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppSettingsService {

    /** Devises proposées dans l'interface d'administration. */
    public static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("EUR", "USD", "XOF", "XAF", "GBP", "CHF", "CAD", "MAD", "NGN");

    /** Langues supportées par le front-end. */
    public static final Set<String> SUPPORTED_LOCALES = Set.of("fr", "en");

    private final AppSettingsRepository appSettingsRepository;
    private final AuditService auditService;

    /**
     * Renvoie les paramètres courants, en créant le singleton avec des
     * valeurs par défaut au premier accès.
     */
    @Transactional(readOnly = true)
    public AppSettingsResponse get() {
        return toResponse(getOrCreate());
    }

    /**
     * Met à jour les paramètres (réservé au SUPER_ADMIN via le contrôleur).
     */
    @Transactional
    public AppSettingsResponse update(AppSettingsResponse.UpdateRequest request) {
        validateUpdate(request);

        AppSettings settings = getOrCreate();
        settings.setCurrency(request.getCurrency().trim().toUpperCase());
        settings.setLocale(request.getLocale().trim().toLowerCase());
        settings.setTimezone(normalize(request.getTimezone()));
        settings.setLowStockEmails(normalize(request.getLowStockEmails()));

        AppSettings saved = appSettingsRepository.save(settings);
        auditService.record("UPDATE", "AppSettings", saved.getId(), "Paramètres application",
                String.format("devise=%s, langue=%s", saved.getCurrency(), saved.getLocale()));

        log.info("Paramètres mis à jour : devise={}, langue={}", saved.getCurrency(), saved.getLocale());
        return toResponse(saved);
    }

    private void validateUpdate(AppSettingsResponse.UpdateRequest request) {
        if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().trim().toUpperCase())) {
            throw new BusinessRuleException("Devise non supportée : " + request.getCurrency()
                    + ". Supportées : " + String.join(", ", SUPPORTED_CURRENCIES));
        }
        if (!SUPPORTED_LOCALES.contains(request.getLocale().trim().toLowerCase())) {
            throw new BusinessRuleException("Langue non supportée : " + request.getLocale());
        }
    }

    private AppSettings getOrCreate() {
        return appSettingsRepository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(() -> appSettingsRepository.save(AppSettings.builder()
                        .id(AppSettings.SINGLETON_ID)
                        .currency("EUR")
                        .locale("fr")
                        .build()));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> splitEmails(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private AppSettingsResponse toResponse(AppSettings settings) {
        return AppSettingsResponse.builder()
                .currency(settings.getCurrency())
                .locale(settings.getLocale())
                .timezone(settings.getTimezone())
                .lowStockEmails(splitEmails(settings.getLowStockEmails()))
                .build();
    }
}
