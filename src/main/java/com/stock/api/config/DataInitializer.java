package com.stock.api.config;

import com.stock.api.entity.Category;
import com.stock.api.entity.Product;
import com.stock.api.entity.Role;
import com.stock.api.entity.Sale;
import com.stock.api.entity.SaleItem;
import com.stock.api.entity.StockMovement;
import com.stock.api.entity.User;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SaleRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Initialise des données de démonstration au démarrage de l'application.
 * Idempotent : chaque élément n'est créé que s'il n'existe pas déjà.
 *
 * Comptes de démonstration (mot de passe = partie avant @, en minuscules) :
 * - superadmin@stock.com  → SUPER_ADMIN + ADMIN (tout faire)
 * - admin@stock.com       → ADMIN (tout faire sauf gestion des superadmins)
 * - manager@stock.com     → MANAGER (produits, catégories, commandes, validation)
 * - gestionnaire@stock.com→ GESTIONNAIRE (entrées de stock uniquement)
 * - vendeur@stock.com     → SELLER (sorties de stock / ventes uniquement)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SaleRepository saleRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Gestion des rôles pour les vendeurs et leurs interfaces (stats temps
     * réel, reçus) — la table user_roles porte une contrainte CHECK sur la
     * colonne role. Si la base provient d'un ancien schéma (avant l'ajout des
     * rôles GESTIONNAIRE / SELLER), la contrainte existante les refuse et la
     * création de vendeurs échoue. Hibernate (ddl-auto=update) ne recrée pas
     * cette contrainte : on la réaligne donc sur l'enum Role au démarrage.
     */
    private static final String DROP_ROLE_CHECK_SQL =
            "ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_role_check";

    private static final String ADD_ROLE_CHECK_SQL =
            "ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_check CHECK (role IN "
                    + "('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'GESTIONNAIRE', 'SELLER', 'USER', 'VIEWER'))";

    /**
     * Unicité des noms de produits et catégories parmi les lignes ACTIVES
     * uniquement (RG-04) : Hibernate génère avec ddl-auto=update une
     * contrainte UNIQUE globale qui bloque la recréation d'un élément au nom
     * d'un élément soft-deleted. On remplace donc ces contraintes par des
     * index partiels PostgreSQL. Les noms des contraintes générées par
     * Hibernate étant imprévisibles (ex. uko61fmio5yukmmiqgnxf8pnavn), on
     * cherche le nom via pg_constraint.
     */
    private static final String DROP_NAME_UNIQUE_SQL = """
            DO $$
            DECLARE
                c record;
            BEGIN
                FOR c IN
                    SELECT con.conname, rel.relname
                    FROM pg_constraint con
                    JOIN pg_class rel ON rel.oid = con.conrelid
                    WHERE con.contype = 'u'
                      AND rel.relname IN ('products', 'categories')
                      AND con.conkey @> ARRAY[
                          (SELECT attnum::smallint FROM pg_attribute
                           WHERE attrelid = rel.oid AND attname = 'name')]::smallint[]
                LOOP
                    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', c.relname, c.conname);
                END LOOP;
            END $$;
            """;

    private static final String ADD_PARTIAL_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_products_name_active ON products (name) WHERE deleted = FALSE;
            CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_name_active ON categories (name) WHERE deleted = FALSE;
            """;

    /**
     * Comptes de secours demandés par l'équipe :
     * - superadmin@mail.com / admin1232 (superadmin de secours)
     *
     * Ces comptes sont créés UNIQUEMENT si aucun compte portant cet email
     * n'existe déjà. Ils ne remplacent pas les comptes démo existants.
     */
    private static final String FALLBACK_SUPERADMIN_EMAIL = "superadmin@mail.com";
    private static final String FALLBACK_SUPERADMIN_PASSWORD = "admin1232";
    private static final String VENDOR_EMAIL = "vendeur@gmail.com";
    private static final String VENDOR_PASSWORD = "vendeur";

    @Override
    @Transactional
    public void run(String... args) {
        realignUserRoleCheckConstraint();
        realignNameUniqueConstraints();
        ensureSalesTableHasCancellationFields();
        User seller = seedAccounts();
        seedFallbackSuperadmin();
        seedVendorAccount();
        seedDemoData(seller);
        User seller2 = userRepository.findByEmail("vendeur2@stock.com").orElse(null);
        seedSecondSellerSales(seller2);
        alignDemoAccountPasswords();
    }

    private void seedFallbackSuperadmin() {
        if (userRepository.existsByEmail(FALLBACK_SUPERADMIN_EMAIL)) {
            return;
        }

        User created = userRepository.save(User.builder()
                .email(FALLBACK_SUPERADMIN_EMAIL)
                .password(passwordEncoder.encode(FALLBACK_SUPERADMIN_PASSWORD))
                .firstName("Super")
                .lastName("Admin")
                .roles(new HashSet<>(Set.of(Role.SUPER_ADMIN, Role.ADMIN)))
                .active(true)
                .build());
        auditService.recordFor("system", "SYSTEM", "CREATE", "User", created.getId(),
                created.getEmail(), "Superadmin de secours créé en fallback au démarrage");
        log.info("Superadmin de secours créé : {} ({})", FALLBACK_SUPERADMIN_EMAIL,
                Set.of(Role.SUPER_ADMIN, Role.ADMIN));
    }

    private void seedVendorAccount() {
        if (userRepository.existsByEmail(VENDOR_EMAIL)) {
            return;
        }

        User created = userRepository.save(User.builder()
                .email(VENDOR_EMAIL)
                .password(passwordEncoder.encode(VENDOR_PASSWORD))
                .firstName("Vendeur")
                .lastName("Test")
                .roles(new HashSet<>(Set.of(Role.SELLER)))
                .active(true)
                .build());
        auditService.recordFor("system", "SYSTEM", "CREATE", "User", created.getId(),
                created.getEmail(), "Compte vendeur créé pour tests");
        log.info("Compte vendeur créé : {} ({})", VENDOR_EMAIL, Set.of(Role.SELLER));
    }

    private void realignNameUniqueConstraints() {
        try {
            jdbcTemplate.execute(DROP_NAME_UNIQUE_SQL);
            jdbcTemplate.execute(ADD_PARTIAL_INDEX_SQL);
            log.info("Contraintes d'unicité des noms (produits/catégories) réalignées sur le soft delete");
        } catch (Exception ex) {
            log.warn("Contraintes d'unicité des noms non réalignées : {}", ex.getMessage());
        }
    }

    private void realignUserRoleCheckConstraint() {
        try {
            jdbcTemplate.execute(DROP_ROLE_CHECK_SQL);
            jdbcTemplate.execute(ADD_ROLE_CHECK_SQL);
        } catch (Exception ex) {
            // Base non PostgreSQL (H2 en tests) : la contrainte gérée par
            // Hibernate y est toujours conforme, rien à faire.
            log.debug("Contrainte user_roles_role_check non réalignée : {}", ex.getMessage());
        }
    }

    private void ensureSalesTableHasCancellationFields() {
        try {
            // Ajout des colonnes de demande d'annulation si elles n'existent pas
            jdbcTemplate.execute(
                "ALTER TABLE sales ADD COLUMN IF NOT EXISTS cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE"
            );
            jdbcTemplate.execute(
                "ALTER TABLE sales ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500)"
            );
            jdbcTemplate.execute(
                "ALTER TABLE sales ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMP"
            );
            log.info("Colonnes de demande d'annulation vérifiées dans la table sales");
        } catch (Exception ex) {
            log.error("Erreur lors de la vérification des colonnes de demande d'annulation : {}", ex.getMessage());
        }
    }

    private record DemoAccount(String email, String password, String firstName,
                               String lastName, Set<Role> roles) {
    }

    private User seedAccounts() {
        List<DemoAccount> accounts = List.of(
                new DemoAccount("superadmin@stock.com", "superadmin", "Super", "Admin",
                        new HashSet<>(Set.of(Role.SUPER_ADMIN, Role.ADMIN))),
                new DemoAccount("admin@stock.com", "admin", "Admin", "Démo",
                        new HashSet<>(Set.of(Role.ADMIN))),
                new DemoAccount("manager@stock.com", "manager", "Manager", "Démo",
                        new HashSet<>(Set.of(Role.MANAGER))),
                new DemoAccount("gestionnaire@stock.com", "gestionnaire", "Gestionnaire", "Démo",
                        new HashSet<>(Set.of(Role.GESTIONNAIRE))),
                new DemoAccount("vendeur@stock.com", "vendeur", "Vendeur", "Démo",
                        new HashSet<>(Set.of(Role.SELLER))),
                new DemoAccount("vendeur2@stock.com", "vendeur2", "Vendeur2", "Démo",
                        new HashSet<>(Set.of(Role.SELLER)))
        );

        User seller = null;
        for (DemoAccount account : accounts) {
            User user = userRepository.findByEmail(account.email()).orElseGet(() -> {
                User created = userRepository.save(User.builder()
                        .email(account.email())
                        .password(passwordEncoder.encode(account.password()))
                        .firstName(account.firstName())
                        .lastName(account.lastName())
                        .roles(new HashSet<>(account.roles()))
                        .active(true)
                        .build());
                auditService.recordFor("system", "SYSTEM", "CREATE", "User", created.getId(),
                        created.getEmail(), "Compte de démonstration créé avec rôles : "
                                + account.roles());
                log.info("Compte de démonstration créé : {} ({})", account.email(), account.roles());
                return created;
            });
            if (account.email().startsWith("vendeur") && seller == null) {
                seller = user;
            }
        }
        return seller;
    }

    private record DemoProduct(String name, String description, String reference,
                               BigDecimal price, int quantity, int alertThreshold) {
    }

    private void seedDemoData(User seller) {
        if (seller == null || !seller.hasRole(Role.SELLER)) {
            return;
        }

        // ── Catégories ──────────────────────────────────────────
        Category electronics = seedCategory("Électronique", "Appareils et accessoires électroniques");
        Category office = seedCategory("Bureau", "Fournitures et mobilier de bureau");

        // ── Produits ────────────────────────────────────────────
        List<DemoProduct> demoProducts = List.of(
                new DemoProduct("Clavier sans fil", "Clavier Bluetooth AZERTY", "KB-BT-001", new BigDecimal("49.99"), 60, 10),
                new DemoProduct("Souris ergonomique", "Souris verticale sans fil", "MOU-ERG-001", new BigDecimal("34.50"), 45, 10),
                new DemoProduct("Écran 24 pouces", "Écran LED Full HD", "ECR-24-001", new BigDecimal("149.00"), 25, 5),
                new DemoProduct("Casque audio", "Casque à réduction de bruit", "CAS-RB-001", new BigDecimal("89.90"), 30, 8),
                new DemoProduct("Carnet A5", "Carnet ligné 120 pages", "CAR-A5-001", new BigDecimal("6.90"), 120, 20),
                new DemoProduct("Stylo bille", "Lot de 10 stylos bleus", "STY-B10-001", new BigDecimal("4.50"), 200, 30)
        );

        Product[] products = new Product[demoProducts.size()];
        for (int i = 0; i < demoProducts.size(); i++) {
            DemoProduct dp = demoProducts.get(i);
            products[i] = seedProduct(dp, i < 4 ? electronics : office, seller);
        }

        // ── Ventes sur les 7 derniers jours ─────────────────────
        seedSales(seller, products);
    }

    private Category seedCategory(String name, String description) {
        return categoryRepository.findByNameAndDeletedFalse(name).orElseGet(() -> {
            Category category = categoryRepository.save(Category.builder()
                    .name(name)
                    .description(description)
                    .build());
            auditService.recordFor("system", "SYSTEM", "CREATE", "Category", category.getId(),
                    name, "Catégorie de démonstration");
            return category;
        });
    }

    private Product seedProduct(DemoProduct dp, Category category, User seller) {
        return productRepository.findByNameAndDeletedFalse(dp.name()).orElseGet(() -> {
            Product product = productRepository.save(Product.builder()
                    .name(dp.name())
                    .description(dp.description())
                    .reference(dp.reference())
                    .price(dp.price())
                    .quantity(0)
                    .alertThreshold(dp.alertThreshold())
                    .category(category)
                    .build());

            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.ENTRY)
                    .product(product)
                    .quantity(dp.quantity())
                    .reason("Stock initial (démo)")
                    .performedBy(seller)
                    .build());
            product.addQuantity(dp.quantity());
            productRepository.save(product);

            auditService.recordFor("system", "SYSTEM", "CREATE", "Product", product.getId(),
                    dp.name(), "Produit de démonstration (stock initial : " + dp.quantity() + ")");
            return product;
        });
    }

    /**
     * Crée une vente COMPLETED datée de maintenant, puis la re-date.
     */
    private void seedSales(User seller, Product[] products) {
        if (saleRepository.count() > 0) {
            log.info("Ventes de démonstration ignorées : des ventes existent déjà en base");
            return;
        }

        // { jour relatif (0 = aujourd'hui), index produit, quantité }
        int[][] plan = {
                {0, 0, 2}, {0, 1, 1},
                {1, 2, 1}, {1, 3, 2},
                {2, 4, 5},
                {3, 5, 10}, {3, 0, 1},
                {4, 1, 3},
                {5, 2, 2}, {5, 3, 1},
                {6, 4, 8}, {6, 5, 6},
        };

        LocalDateTime now = LocalDateTime.now();
        int saleIndex = 0;
        for (int[] line : plan) {
            int daysAgo = line[0];
            Product product = products[line[1]];
            int quantity = line[2];

            if (!product.canRemoveQuantity(quantity)) {
                continue;
            }

            Sale sale = Sale.builder()
                    .reference("VTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .seller(seller)
                    .status(Sale.SaleStatus.COMPLETED)
                    .notes("Vente de démonstration")
                    .build();

            SaleItem item = SaleItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .subtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                    .build();
            sale.addItem(item);

            Sale saved = saleRepository.save(sale);

            product.removeQuantity(quantity);
            productRepository.save(product);
            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.EXIT)
                    .product(product)
                    .quantity(quantity)
                    .reason("Vente " + saved.getReference() + " (démo)")
                    .performedBy(seller)
                    .build());

            auditService.recordFor("system", "SYSTEM", "EXIT", "StockMovement", saved.getId(),
                    product.getName(), "Vente de démonstration qty=" + quantity);

            saleRepository.backdateSale(saved.getId(),
                    now.minusDays(daysAgo).withHour(10 + (saleIndex % 6)).withMinute(saleIndex * 7 % 60));
            saleIndex++;
        }

        log.info("{} ventes de démonstration créées sur les 7 derniers jours", saleIndex);
    }

    /**
     * Ventes de démonstration pour le second vendeur (comparaison utile dans
     * les stats admin). Idempotent : saute si ce vendeur a déjà des ventes.
     */
    /**
     * Répare les mots de passe des comptes de démonstration connus dont le
     * hash en base ne correspond plus au mot de passe documenté (partie avant
     * @ du mail, en minuscules) — par exemple si une ancienne version écrasait
     * les mots de passe au démarrage. Ne touche qu'aux comptes connus et uniquement
     * si le mot de passe stocké ne correspond plus, afin de préserver les
     * modifications volontaires et les comptes réels.
     */
    private void alignDemoAccountPasswords() {
        List<DemoAccount> accounts = List.of(
                new DemoAccount("superadmin@stock.com", "superadmin", "Super", "Admin",
                        new HashSet<>(Set.of(Role.SUPER_ADMIN, Role.ADMIN))),
                new DemoAccount("admin@stock.com", "admin", "Admin", "Démo",
                        new HashSet<>(Set.of(Role.ADMIN))),
                new DemoAccount("manager@stock.com", "manager", "Manager", "Démo",
                        new HashSet<>(Set.of(Role.MANAGER))),
                new DemoAccount("gestionnaire@stock.com", "gestionnaire", "Gestionnaire", "Démo",
                        new HashSet<>(Set.of(Role.GESTIONNAIRE))),
                new DemoAccount("vendeur@stock.com", "vendeur", "Vendeur", "Démo",
                        new HashSet<>(Set.of(Role.SELLER))),
                new DemoAccount("vendeur2@stock.com", "vendeur2", "Vendeur2", "Démo",
                        new HashSet<>(Set.of(Role.SELLER))),
                new DemoAccount(FALLBACK_SUPERADMIN_EMAIL, FALLBACK_SUPERADMIN_PASSWORD, "Super", "Admin",
                        new HashSet<>(Set.of(Role.SUPER_ADMIN, Role.ADMIN))),
                new DemoAccount(VENDOR_EMAIL, VENDOR_PASSWORD, "Vendeur", "Test",
                        new HashSet<>(Set.of(Role.SELLER)))
        );

        int repaired = 0;
        for (DemoAccount account : accounts) {
            User user = userRepository.findByEmail(account.email()).orElse(null);
            if (user == null || passwordEncoder.matches(account.password(), user.getPassword())) {
                continue;
            }
            user.setPassword(passwordEncoder.encode(account.password()));
            userRepository.save(user);
            repaired++;
            log.info("Mot de passe du compte de démonstration {} réaligné sur la valeur documentée",
                    account.email());
        }
        if (repaired > 0) {
            log.info("{} mot(s) de passe de comptes de démonstration réaligné(s)", repaired);
        }
    }

    private void seedSecondSellerSales(User seller2) {
        if (seller2 == null || !seller2.hasRole(Role.SELLER)) {
            return;
        }
        if (saleRepository.countBySellerId(seller2.getId()) > 0) {
            return;
        }

        Product product = productRepository.findByNameAndDeletedFalse("Stylo bille").orElse(null);
        if (product == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int[][] plan = {
                {0, 4}, {1, 2}, {3, 6}, {5, 3},
        }; // { jours écoulés, quantité }

        int saleIndex = 0;
        for (int[] line : plan) {
            int daysAgo = line[0];
            int quantity = line[1];

            if (!product.canRemoveQuantity(quantity)) {
                continue;
            }

            Sale sale = Sale.builder()
                    .reference("VTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .seller(seller2)
                    .status(Sale.SaleStatus.COMPLETED)
                    .paymentMethod(Sale.PaymentMethod.CARD)
                    .notes("Vente de démonstration")
                    .build();

            SaleItem item = SaleItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .subtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                    .build();
            sale.addItem(item);

            Sale saved = saleRepository.save(sale);

            product.removeQuantity(quantity);
            productRepository.save(product);
            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.EXIT)
                    .product(product)
                    .quantity(quantity)
                    .reason("Vente " + saved.getReference() + " (démo)")
                    .performedBy(seller2)
                    .build());

            saleRepository.backdateSale(saved.getId(),
                    now.minusDays(daysAgo).withHour(11 + (saleIndex % 4)).withMinute(30));
            saleIndex++;
        }

        log.info("{} ventes de démonstration créées pour le second vendeur", saleIndex);
    }
}
