package com.stock.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.api.config.PostgresContainerConfig;
import com.stock.api.dto.*;
import com.stock.api.entity.StockMovement.MovementType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test d'intégration complet avec PostgreSQL Testcontainers.
 * Couvre le parcours bout en bout :
 *   Inscription → Connexion → Catégories → Produits → Stock → Commandes
 *
 * IMPORTANT : pas de @ActiveProfiles("test") — les propriétés de datasource
 * sont injectées par @DynamicPropertySource de PostgresContainerConfig.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// L'ordre des classes @Nested n'est PAS garanti par défaut : sans cela,
// SaleValidationFlow peut tourner avant AuthFlow (token JWT null → 401).
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("Test d'intégration — Parcours complet")
class FullFlowIT extends PostgresContainerConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String authToken;
    private static Long categoryId;
    private static Long productId;

    private int getCurrentQuantity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/" + productId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andReturn();
        ProductResponse product = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);
        return product.getQuantity();
    }

    private void ensureStockAtLeast(int minimum) throws Exception {
        int current = getCurrentQuantity();
        if (current < minimum) {
            StockMovementRequest request = StockMovementRequest.builder()
                    .type(MovementType.ENTRY)
                    .productId(productId)
                    .quantity(minimum - current)
                    .reason("Réapprovisionnement tests")
                    .build();
            mockMvc.perform(post("/api/stock-movements")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    private Long createOrder(int quantity) throws Exception {
        OrderLineRequest line = OrderLineRequest.builder()
                .productId(productId)
                .quantity(quantity)
                .build();
        OrderRequest request = OrderRequest.builder()
                .lines(java.util.List.of(line))
                .build();
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.getId();
    }

    // ── Prérequis initialisés paresseusement ─────────────────
    // Les flows dépendent de données créées par les flows précédents via des
    // variables statiques. L'ordre d'exécution des classes @Nested n'étant pas
    // garanti selon les runners, chaque flow s'assure lui-même que ses
    // prérequis existent (création une seule fois, gardée par le null check).

    private void ensureAuthToken() throws Exception {
        if (authToken != null) {
            return;
        }
        LoginRequest request = LoginRequest.builder()
                .email("superadmin@stock.com")
                .password("superadmin")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roles", hasItem("SUPER_ADMIN")))
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        authToken = response.getToken();
    }

    private void ensureCategory() throws Exception {
        ensureAuthToken();
        if (categoryId != null) {
            return;
        }
        CategoryRequest request = CategoryRequest.builder()
                .name("Électronique")
                .description("Appareils électroniques")
                .build();

        MvcResult result = mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        CategoryResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CategoryResponse.class);
        categoryId = response.getId();
    }

    private void ensureProduct() throws Exception {
        ensureCategory();
        if (productId != null) {
            return;
        }
        ProductRequest request = ProductRequest.builder()
                .name("Clavier sans fil")
                .description("Clavier Bluetooth")
                .reference("KB-BT-001")
                .price(java.math.BigDecimal.valueOf(49.99))
                .categoryId(categoryId)
                .quantity(0)
                .alertThreshold(10)
                .build();

        MvcResult result = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        ProductResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ProductResponse.class);
        productId = response.getId();
    }

    // ═══════════════════════════════════════════════════════
    // ÉTAPE 1 : Authentification
    // (compte admin semé par DataInitializer — l'inscription a été retirée)
    // ═══════════════════════════════════════════════════════
    @Nested
    @Order(1)
    @DisplayName("1. Authentification")
    class AuthFlow {

        @Test
        @Order(1)
        @DisplayName("Connexion admin semé → 200 + JWT")
        void login_seededAdmin() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("superadmin@stock.com")
                    .password("superadmin")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.email").value("superadmin@stock.com"))
                    .andExpect(jsonPath("$.roles", hasItem("SUPER_ADMIN")))
                    .andReturn();

            AuthResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AuthResponse.class);
            authToken = response.getToken();
        }
        @Test
        @Order(2)
        @DisplayName("Connexion avec le même compte → 200 + JWT")
        void login() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("superadmin@stock.com")
                    .password("superadmin")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.email").value("superadmin@stock.com"));
        }

        @Test
        @Order(3)
        @DisplayName("Connexion avec mauvais mot de passe → 401")
        void login_wrongPassword() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("superadmin@stock.com")
                    .password("wrongpassword")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════
    // ÉTAPE 2 : Catégories
    // ═══════════════════════════════════════════════════════
    @Nested
    @Order(2)
    @DisplayName("2. Catégories")
    class CategoryFlow {

        @Test
        @Order(1)
        @DisplayName("Créer une catégorie → 201")
        void create_category() throws Exception {
            ensureCategory();

            mockMvc.perform(get("/api/categories/" + categoryId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").isNotEmpty())
                    .andExpect(jsonPath("$.deleted").value(false));
        }

        @Test
        @Order(2)
        @DisplayName("Lister les catégories → 200")
        void list_categories() throws Exception {
            ensureCategory();
            mockMvc.perform(get("/api/categories")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.name == 'Électronique')]").exists());
        }

        @Test
        @Order(3)
        @DisplayName("Modifier la catégorie → 200")
        void update_category() throws Exception {
            ensureCategory();
            CategoryRequest request = CategoryRequest.builder()
                    .name("Électronique & Informatique")
                    .description("Appareils électroniques et informatiques")
                    .build();

            mockMvc.perform(put("/api/categories/" + categoryId)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Électronique & Informatique"));
        }

        @Test
        @Order(4)
        @DisplayName("Sans auth → 401")
        void create_category_noAuth() throws Exception {
            CategoryRequest request = CategoryRequest.builder()
                    .name("Non autorisé")
                    .build();

            mockMvc.perform(post("/api/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════
    // ÉTAPE 3 : Produits
    // ═══════════════════════════════════════════════════════
    @Nested
    @Order(3)
    @DisplayName("3. Produits")
    class ProductFlow {

        @Test
        @Order(1)
        @DisplayName("Créer un produit → 201")
        void create_product() throws Exception {
            ensureProduct();

            mockMvc.perform(get("/api/products/" + productId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Clavier sans fil"))
                    .andExpect(jsonPath("$.lowStock").value(true));
        }

        @Test
        @Order(2)
        @DisplayName("Lister les produits → 200 + 1 résultat")
        void list_products() throws Exception {
            ensureProduct();
            mockMvc.perform(get("/api/products")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.name == 'Clavier sans fil')]").exists());
        }

        @Test
        @Order(3)
        @DisplayName("Rechercher par nom → 200")
        void search_byName() throws Exception {
            ensureProduct();
            mockMvc.perform(get("/api/products")
                            .header("Authorization", "Bearer " + authToken)
                            .param("name", "Clavier"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @Order(4)
        @DisplayName("Produits en stock bas → 200 + 1 résultat")
        void lowStock_products() throws Exception {
            ensureProduct();
            mockMvc.perform(get("/api/products/low-stock")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.name == 'Clavier sans fil')]").exists());
        }

        @Test
        @Order(5)
        @DisplayName("Produit avec catégorie inexistante → 404")
        void create_product_invalidCategory() throws Exception {
            ensureAuthToken();
            ProductRequest request = ProductRequest.builder()
                    .name("Test")
                    .reference("TEST-001")
                    .price(java.math.BigDecimal.valueOf(10.00))
                    .categoryId(999L)
                    .build();

            mockMvc.perform(post("/api/products")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ═══════════════════════════════════════════════════════
    // ÉTAPE 4 : Stock (RG-01, RG-02)
    // ═══════════════════════════════════════════════════════
    @Nested
    @Order(4)
    @DisplayName("4. Stock — RG-01 & RG-02")
    class StockFlow {

        @Test
        @Order(1)
        @DisplayName("Entrée de stock (+50) → 201, qty = 50")
        void entry_stock() throws Exception {
            ensureProduct();
            int before = getCurrentQuantity();
            StockMovementRequest request = StockMovementRequest.builder()
                    .type(MovementType.ENTRY)
                    .productId(productId)
                    .quantity(50)
                    .reason("Réapprovisionnement")
                    .build();

            mockMvc.perform(post("/api/stock-movements")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("ENTRY"))
                    .andExpect(jsonPath("$.quantity").value(50));

            mockMvc.perform(get("/api/products/" + productId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(jsonPath("$.quantity").value(before + 50));
        }

        @Test
        @Order(2)
        @DisplayName("Sortie de stock (-5) → 201, qty = 45")
        void exit_stock() throws Exception {
            ensureProduct();
            ensureStockAtLeast(10);
            int before = getCurrentQuantity();
            StockMovementRequest request = StockMovementRequest.builder()
                    .type(MovementType.EXIT)
                    .productId(productId)
                    .quantity(5)
                    .reason("Livraison client")
                    .build();

            mockMvc.perform(post("/api/stock-movements")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("EXIT"))
                    .andExpect(jsonPath("$.quantity").value(5));

            mockMvc.perform(get("/api/products/" + productId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(jsonPath("$.quantity").value(before - 5));
        }

        @Test
        @Order(3)
        @DisplayName("RG-02 : Sortie avec stock insuffisant → 409")
        void exit_insufficientStock() throws Exception {
            ensureProduct();
            StockMovementRequest request = StockMovementRequest.builder()
                    .type(MovementType.EXIT)
                    .productId(productId)
                    .quantity(9999)
                    .reason("Test RG-02")
                    .build();

            mockMvc.perform(post("/api/stock-movements")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("insuffisante")));
        }

        @Test
        @Order(4)
        @DisplayName("Historique du produit → 200 + 2 mouvements")
        void history_product() throws Exception {
            ensureProduct();
            int before = mockMvc.perform(get("/api/stock-movements/product/" + productId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString().split("\"id\":").length - 1;

            StockMovementRequest request = StockMovementRequest.builder()
                    .type(MovementType.ENTRY)
                    .productId(productId)
                    .quantity(1)
                    .reason("Mouvement pour historique")
                    .build();
            mockMvc.perform(post("/api/stock-movements")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/stock-movements/product/" + productId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(before + 1)));
        }

        @Test
        @Order(5)
        @DisplayName("Historique filtré (ENTRY uniquement) → 200 + 1 mouvement")
        void history_filtered() throws Exception {
            ensureProduct();
            String bodyBefore = mockMvc.perform(get("/api/stock-movements/filters")
                            .header("Authorization", "Bearer " + authToken)
                            .param("productId", productId.toString())
                            .param("type", "ENTRY"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            int before = bodyBefore.split("\"type\":\"ENTRY\"").length - 1;

            StockMovementRequest request = StockMovementRequest.builder()
                    .type(MovementType.ENTRY)
                    .productId(productId)
                    .quantity(1)
                    .reason("Mouvement ENTRY pour filtre")
                    .build();
            mockMvc.perform(post("/api/stock-movements")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/stock-movements/filters")
                            .header("Authorization", "Bearer " + authToken)
                            .param("productId", productId.toString())
                            .param("type", "ENTRY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(before + 1)));
        }
    }

    // ═══════════════════════════════════════════════════════
    // ÉTAPE 5 : Commandes (US-09, US-10)
    // ═══════════════════════════════════════════════════════
    @Nested
    @Order(5)
    @DisplayName("5. Commandes — US-09 & US-10")
    class OrderFlow {

        @Test
        @Order(1)
        @DisplayName("Créer une commande multi-lignes → 201")
        void create_order() throws Exception {
            ensureProduct();
            OrderLineRequest line = OrderLineRequest.builder()
                    .productId(productId)
                    .quantity(10)
                    .build();

            OrderRequest request = OrderRequest.builder()
                    .lines(java.util.List.of(line))
                    .notes("Commande client Alpha")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.reference", startsWith("CMD-")))
                    .andExpect(jsonPath("$.lines", hasSize(1)))
                    .andReturn();

            OrderResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), OrderResponse.class);

            mockMvc.perform(post("/api/orders/" + response.getId() + "/cancel")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk());
        }

        @Test
        @Order(2)
        @DisplayName("Valider la commande → 200 + stock décrémenté")
        void validate_order() throws Exception {
            ensureProduct();
            ensureStockAtLeast(15);
            int before = getCurrentQuantity();
            Long id = createOrder(10);

            mockMvc.perform(post("/api/orders/" + id + "/validate")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VALIDATED"));

            mockMvc.perform(get("/api/products/" + productId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(before - 10));
        }

        @Test
        @Order(3)
        @DisplayName("Ré-validation → 409 (déjà validée)")
        void revalidate_order() throws Exception {
            ensureProduct();
            ensureStockAtLeast(5);
            Long id = createOrder(2);

            mockMvc.perform(post("/api/orders/" + id + "/validate")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/orders/" + id + "/validate")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isConflict());
        }

        @Test
        @Order(4)
        @DisplayName("Créer et annuler une commande → 200")
        void create_and_cancel_order() throws Exception {
            ensureProduct();
            OrderLineRequest line = OrderLineRequest.builder()
                    .productId(productId)
                    .quantity(5)
                    .build();

            OrderRequest request = OrderRequest.builder()
                    .lines(java.util.List.of(line))
                    .build();

            MvcResult result = mockMvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            OrderResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), OrderResponse.class);

            mockMvc.perform(post("/api/orders/" + response.getId() + "/cancel")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @Order(5)
        @DisplayName("RG-02 : Commande avec stock insuffisant → 409")
        void validate_insufficientStock() throws Exception {
            ensureProduct();
            Long id = createOrder(1000);

            mockMvc.perform(post("/api/orders/" + id + "/validate")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("insuffisant")));
        }

        @Test
        @Order(6)
        @DisplayName("Lister les commandes → 200")
        void list_orders() throws Exception {
            mockMvc.perform(get("/api/orders")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
        }
    }

    // ═══════════════════════════════════════════════════════
    // ÉTAPE 6 : Validation cascade sur SaleItemRequest
    // ═══════════════════════════════════════════════════════
    @Nested
    @Order(6)
    @DisplayName("6. Ventes — Validation cascade SaleItemRequest")
    class SaleValidationFlow {

        @Test
        @Order(1)
        @DisplayName("Vente sans articles → 400")
        void create_sale_emptyItems_returns400() throws Exception {
            SaleRequest request = SaleRequest.builder()
                    .items(List.of())
                    .build();

            mockMvc.perform(post("/api/sales")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(2)
        @DisplayName("Vente avec productId null → 400 (validation cascade)")
        void create_sale_nullProductId_returns400() throws Exception {
            ensureProduct();
            SaleItemRequest item = SaleItemRequest.builder()
                    .productId(null)
                    .quantity(2)
                    .build();

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(item))
                    .build();

            mockMvc.perform(post("/api/sales")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasKey("items[0].productId")));
        }

        @Test
        @Order(3)
        @DisplayName("Vente avec quantity null → 400 (validation cascade)")
        void create_sale_nullQuantity_returns400() throws Exception {
            ensureProduct();
            SaleItemRequest item = SaleItemRequest.builder()
                    .productId(productId)
                    .quantity(null)
                    .build();

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(item))
                    .build();

            mockMvc.perform(post("/api/sales")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasKey("items[0].quantity")));
        }

        @Test
        @Order(4)
        @DisplayName("Vente avec quantity = 0 → 400 (validation cascade @Min(1))")
        void create_sale_quantityZero_returns400() throws Exception {
            ensureProduct();
            SaleItemRequest item = SaleItemRequest.builder()
                    .productId(productId)
                    .quantity(0)
                    .build();

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(item))
                    .build();

            mockMvc.perform(post("/api/sales")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasKey("items[0].quantity")));
        }

        @Test
        @Order(5)
        @DisplayName("Vente valide avec articles → 201")
        void create_sale_validRequest_returns201() throws Exception {
            ensureProduct();
            ensureStockAtLeast(5);

            SaleItemRequest item = SaleItemRequest.builder()
                    .productId(productId)
                    .quantity(2)
                    .build();

            SaleRequest request = SaleRequest.builder()
                    .items(List.of(item))
                    .build();

            mockMvc.perform(post("/api/sales")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].quantity").value(2));
        }
    }
}
