package com.stock.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration OpenAPI/Swagger avec support JWT.
 * Permet le bouton "Authorize" dans Swagger UI pour tester les endpoints protégés.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Stock API")
                        .version("1.0.0")
                        .description("""
                                API REST de gestion de stock.

                                ## Fonctionnalités
                                - **Authentification** : connexion, JWT (comptes créés par l'administration)
                                - **Catégories** : CRUD avec suppression logique (RG-04)
                                - **Produits** : CRUD, recherche/filtrage, alerte stock bas
                                - **Stock** : entrées/sorties, historique filtrable, RG-02
                                - **Commandes** : création multi-lignes, validation avec déclenchement stock

                                ## Authentification
                                1. Connectez-vous via `POST /api/auth/login`
                                2. Copiez le token JWT retourné
                                3. Cliquez sur **Authorize** ci-dessous et collez le token

                                ## Règles de gestion
                                - **RG-01** : Quantité en stock jamais négative
                                - **RG-02** : Sortie rejetée si stock insuffisant
                                - **RG-04** : Suppression logique (soft delete)
                                - **RG-05** : Un utilisateur a toujours au moins un rôle actif
                                """)
                        .contact(new Contact()
                                .name("Équipe Stock API")
                                .email("contact@stock-api.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Entrez le token JWT sans le préfixe 'Bearer '")));
    }
}
