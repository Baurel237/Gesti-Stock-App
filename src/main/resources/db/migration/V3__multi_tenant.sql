-- ============================================================
-- V3 : socle multi-entreprises
--   - table companies (+ entreprise par défaut « Démo »)
--   - colonne company_id sur les tables métier + backfill
--   - app_settings : la configuration devient propre à chaque entreprise
--   - users.company_id reste NULLABLE : NULL = compte plateforme (SUPER_ADMIN)
-- Note : les blocs DO $$ conditionnels rendent la migration idempotente et
-- tolérante aux bases fraîches (tables déjà créées par Hibernate).
-- ============================================================

-- 1. Table des entreprises -------------------------------------------------
CREATE TABLE IF NOT EXISTS companies (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    warehouse_enabled BOOLEAN  NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_companies_slug UNIQUE (slug)
);

-- Entreprise par défaut : toutes les données existantes y sont rattachées.
INSERT INTO companies (name, slug, active, warehouse_enabled, created_at, updated_at)
SELECT 'Démo', 'demo', TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM companies);

-- 2. company_id sur les tables métier (nullable d'abord, NOT NULL après backfill)
ALTER TABLE users              ADD COLUMN IF NOT EXISTS company_id BIGINT REFERENCES companies(id);
ALTER TABLE products           ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE categories         ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE sales              ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE orders             ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE stock_movements    ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE product_comments   ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE sale_edit_requests ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE audit_logs         ADD COLUMN IF NOT EXISTS company_id BIGINT;

-- Backfill vers l'entreprise par défaut (id = min), puis contraintes NOT NULL
-- sur les tables métier (users reste nullable pour les SUPER_ADMIN plateforme).
DO $$
DECLARE
    def_company BIGINT;
BEGIN
    SELECT MIN(id) INTO def_company FROM companies;

    UPDATE users              SET company_id = def_company WHERE company_id IS NULL;
    UPDATE products           SET company_id = def_company WHERE company_id IS NULL;
    UPDATE categories         SET company_id = def_company WHERE company_id IS NULL;
    UPDATE sales              SET company_id = def_company WHERE company_id IS NULL;
    UPDATE orders             SET company_id = def_company WHERE company_id IS NULL;
    UPDATE stock_movements    SET company_id = def_company WHERE company_id IS NULL;
    UPDATE product_comments   SET company_id = def_company WHERE company_id IS NULL;
    UPDATE sale_edit_requests SET company_id = def_company WHERE company_id IS NULL;
    UPDATE audit_logs         SET company_id = def_company WHERE company_id IS NULL;

    ALTER TABLE products           ALTER COLUMN company_id SET NOT NULL;
    ALTER TABLE categories         ALTER COLUMN company_id SET NOT NULL;
    ALTER TABLE sales              ALTER COLUMN company_id SET NOT NULL;
    ALTER TABLE orders             ALTER COLUMN company_id SET NOT NULL;
    ALTER TABLE stock_movements    ALTER COLUMN company_id SET NOT NULL;
    ALTER TABLE product_comments   ALTER COLUMN company_id SET NOT NULL;
    ALTER TABLE sale_edit_requests ALTER COLUMN company_id SET NOT NULL;
END $$;

-- 3. app_settings : la configuration devient par entreprise -----------------
--    La table peut avoir été créée par Hibernate (id BIGINT, singleton=1) :
--    on la reconstruit proprement avec company_id en PK.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_settings') THEN
        DROP TABLE app_settings;
    END IF;
END $$;

CREATE TABLE app_settings (
    company_id      BIGINT PRIMARY KEY REFERENCES companies(id),
    currency        VARCHAR(10)  NOT NULL DEFAULT 'EUR',
    locale          VARCHAR(5)   NOT NULL DEFAULT 'fr',
    timezone        VARCHAR(50),
    low_stock_emails VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

INSERT INTO app_settings (company_id, currency, locale, created_at, updated_at)
SELECT id, 'EUR', 'fr', NOW(), NOW() FROM companies
WHERE NOT EXISTS (SELECT 1 FROM app_settings);

-- 4. Index et unicité par entreprise ---------------------------------------
--    Unicité portée par entreprise (la référence produit VTE-/REF- n'est
--    unique qu'au sein d'une même entreprise).
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_company_reference
    ON sales (company_id, reference);
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_company_reference
    ON products (company_id, reference) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_company_name
    ON categories (company_id, name) WHERE deleted = FALSE;

-- Index de filtrage (toutes les requêtes listes filtreront par company_id)
CREATE INDEX IF NOT EXISTS idx_products_company        ON products (company_id);
CREATE INDEX IF NOT EXISTS idx_categories_company      ON categories (company_id);
CREATE INDEX IF NOT EXISTS idx_sales_company           ON sales (company_id);
CREATE INDEX IF NOT EXISTS idx_orders_company          ON orders (company_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_company ON stock_movements (company_id);
CREATE INDEX IF NOT EXISTS idx_users_company           ON users (company_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_company      ON audit_logs (company_id);
CREATE INDEX IF NOT EXISTS idx_product_comments_company ON product_comments (company_id);
CREATE INDEX IF NOT EXISTS idx_sale_edit_requests_company ON sale_edit_requests (company_id);
