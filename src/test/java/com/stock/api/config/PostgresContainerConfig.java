package com.stock.api.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Configuration de base pour les tests d'intégration avec PostgreSQL Testcontainers.
 *
 * Utilise un conteneur statique partagé entre tous les tests qui étendent cette classe.
 * Le conteneur est démarré une seule fois et arrêté à la fin.
 */
public abstract class PostgresContainerConfig {

    // Mode « PostgreSQL externe » : si USE_EXTERNAL_PG=true, on n'utilise pas
    // Testcontainers (utile en local quand Docker n'est pas détecté par
    // Testcontainers) mais un PostgreSQL déjà lancé (ex. docker compose).
    // Les paramètres de connexion sont lus dans des variables DÉDIÉES
    // (E2E_PG_*) et non SPRING_DATASOURCE_* : ces dernières signifient
    // quelque chose pour Spring et fuiteraient dans les tests unitaires
    // H2 tournant dans le même process Maven (driver H2 rejetant l'URL).
    private static final boolean USE_EXTERNAL_PG =
            Boolean.parseBoolean(System.getenv().getOrDefault("USE_EXTERNAL_PG", "false"));

    // Conteneur unique partagé — démarré statiquement
    // pour que @DynamicPropertySource puisse accéder aux URLs
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("stock_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Démarrer le conteneur si pas encore lancé
        if (!USE_EXTERNAL_PG && !postgres.isRunning()) {
            postgres.start();
        }

        if (USE_EXTERNAL_PG) {
            registry.add("spring.datasource.url",
                    () -> System.getenv().getOrDefault("E2E_PG_URL",
                            "jdbc:postgresql://localhost:5432/stock_test"));
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("E2E_PG_USERNAME", "stock_user"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("E2E_PG_PASSWORD", "stock_password"));
        } else {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Les IT valident maintenant le même schéma que la production :
        // Flyway exécute V1→V5 sur le conteneur, Hibernate se borne à
        // valider les entités contre le schéma migré (ddl-auto=validate).
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Le profil « test » (ressources de test, qui masquent celles de main
        // sur le classpath) désactive Flyway : on le réactive explicitement,
        // sinon validate s'exécuterait sur une base vide.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.show-sql", () -> "true");
        // FullFlowIT part d'une base vide (il crée ses propres données) :
        // pas de catégories/produits/ventes de démonstration de DataInitializer.
        registry.add("stock.data-initializer.seed-demo-data", () -> "false");
    }
}
