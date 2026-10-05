package com.stock.api.service;

import com.stock.api.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Taux de change réels (nouvelle exigence devise) : récupère les taux de
 * l'API publique ExchangeRate-API (open.er-api.com, gratuit, sans clé,
 * mise à jour quotidienne) via le backend — jamais depuis le navigateur.
 *
 * Le résultat est mis en cache mémoire pendant {@link #CACHE_TTL} :
 * un seuil de requêtes par instance, tolérance aux pannes de l'API (le
 * dernier cache valide reste servi) et secours sur les taux fixes internes
 * si aucun taux n'a jamais pu être récupéré.
 */
@Service
@Slf4j
public class FxRateService {

    /** Devises supportées par l'application (settings.types SUPPORTED_CURRENCIES). */
    private static final Set<String> SUPPORTED = Set.of(
            "EUR", "USD", "XOF", "XAF", "GBP", "CHF", "CAD", "MAD", "NGN");

    /** Durée de vie du cache des taux : l'API se met à jour quotidiennement. */
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final RestClient restClient;
    private final String ratesUrl;

    /** Cache : code devise → taux vis-à-vis de la devise de base demandée. */
    private final Map<String, FxRates> cacheByBase = new ConcurrentHashMap<>();

    public FxRateService(
            @Value("${fx.rates.url:https://open.er-api.com/v6/latest}") String ratesUrl,
            @Value("${fx.rates.timeout-seconds:5}") long timeoutSeconds) {
        this.ratesUrl = ratesUrl;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) (timeoutSeconds * 1000));
                    setReadTimeout((int) (timeoutSeconds * 1000));
                }})
                .build();
    }

    /**
     * Taux réels vis-à-vis de la devise de base demandée.
     *  - cache valide (< 12 h) → servi directement ;
     *  - sinon → appel API (mis en cache si succès) ;
     *  - échec API → dernier cache connu même périmé ;
     *  - jamais de cache → exception métier (le frontend retombera sur ses
     *    taux internes).
     */
    public FxRates getRates(String baseCurrency) {
        String base = normalize(baseCurrency);
        FxRates cached = cacheByBase.get(base);
        if (cached != null && cached.isFresh()) {
            return cached;
        }
        try {
            // URI construite directement : un template « {url}/{base} » ferait
            // encoder https:// comme variable d'URI (percent-encoding).
            ExternalRatesResponse response = restClient.get()
                    .uri(ratesUrl + "/" + base)
                    .retrieve()
                    .body(ExternalRatesResponse.class);
            if (response == null || !"success".equals(response.result()) || response.rates() == null) {
                throw new IllegalStateException("Réponse API de taux invalide");
            }
            Map<String, Double> filtered = response.rates().entrySet().stream()
                    .filter(e -> SUPPORTED.contains(e.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            FxRates rates = new FxRates(base, filtered, Instant.now(), response.timeLastUpdateUtc());
            cacheByBase.put(base, rates);
            log.info("Taux de change rafraîchis (base {}) : {} devises", base, filtered.size());
            return rates;
        } catch (Exception e) {
            if (cached != null) {
                log.warn("API de taux indisponible ({}), service du cache périmé (base {})",
                        e.getMessage(), base);
                return cached;
            }
            log.error("Impossible de récupérer les taux de change (base {}) : {}", base, e.getMessage());
            throw new BusinessRuleException(
                    "Taux de change indisponibles. Vérifiez la connexion du serveur vers "
                            + ratesUrl + " ou réessayez plus tard.");
        }
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Devise de base requise.");
        }
        String base = code.trim().toUpperCase();
        if (!SUPPORTED.contains(base)) {
            throw new BusinessRuleException("Devise non supportée : " + base);
        }
        return base;
    }

    /** Instantané de taux : base + devises supportées + fraîcheur. */
    public record FxRates(
            String base,
            Map<String, Double> rates,
            Instant fetchedAt,
            String providerUpdatedAt) {

        boolean isFresh() {
            return fetchedAt != null && Duration.between(fetchedAt, Instant.now()).compareTo(CACHE_TTL) < 0;
        }
    }

    /** Sous-ensemble de la réponse open.er-api.com. */
    record ExternalRatesResponse(String result, String time_last_update_utc, Map<String, Double> rates) {
        String timeLastUpdateUtc() {
            return time_last_update_utc;
        }
    }
}
