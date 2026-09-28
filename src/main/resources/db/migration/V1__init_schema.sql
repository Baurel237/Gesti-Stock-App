-- ============================================================
-- V1 : initialisation du schéma de base (miroir exact du schéma
-- historique généré par Hibernate avant l'arrivée de Flyway).
--
-- Contexte : ce projet a démarré avec ddl-auto=update ; les migrations
-- V2+ supposaient les tables préexistantes (baseline-on-migrate=1).
-- Le passage à ddl-auto=validate exige un schéma 100 % migré :
-- cette V1 le crée intégralement, ce qui permet aussi de démarrer
-- sur une base vierge sans Hibernate.
--
-- Tolérante aux bases existantes créées par Hibernate (IF NOT EXISTS)
-- afin de rester cohérente avec baseline-version=1.
-- ============================================================

-- ── companies : créée d'abord (référencée par les autres tables) ──
CREATE TABLE IF NOT EXISTS companies (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL,
    active            BOOLEAN      NOT NULL,
    warehouse_enabled BOOLEAN      NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_companies_slug UNIQUE (slug)
);

-- ── users ──
CREATE TABLE IF NOT EXISTS users (
    id                 BIGSERIAL PRIMARY KEY,
    email              VARCHAR(100) NOT NULL,
    password           VARCHAR(255) NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    company_id         BIGINT,
    active             BOOLEAN      NOT NULL,
    preferred_currency VARCHAR(3),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT       NOT NULL REFERENCES users (id),
    role    VARCHAR(255) NOT NULL
);

-- ── categories ──
CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    company_id  BIGINT       NOT NULL,
    deleted     BOOLEAN      NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- ── products ──
CREATE TABLE IF NOT EXISTS products (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(100)  NOT NULL,
    description        VARCHAR(500),
    reference          VARCHAR(50)   NOT NULL,
    image_path         VARCHAR(255),
    image_content_type VARCHAR(50),
    quantity           INTEGER       NOT NULL,
    alert_threshold    INTEGER       NOT NULL,
    price              NUMERIC(10,2) NOT NULL,
    category_id        BIGINT        NOT NULL,
    company_id         BIGINT        NOT NULL,
    deleted            BOOLEAN       NOT NULL,
    created_at         TIMESTAMP     NOT NULL,
    updated_at         TIMESTAMP     NOT NULL
);

-- ── stock_movements ──
CREATE TABLE IF NOT EXISTS stock_movements (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(20)  NOT NULL,
    product_id      BIGINT       NOT NULL,
    quantity        INTEGER      NOT NULL,
    reason          VARCHAR(500),
    order_id        BIGINT,
    performed_by_id BIGINT       NOT NULL,
    company_id      BIGINT       NOT NULL,
    warehouse_id    BIGINT,
    created_at      TIMESTAMP    NOT NULL
);

-- ── orders ──
CREATE TABLE IF NOT EXISTS orders (
    id            BIGSERIAL PRIMARY KEY,
    reference     VARCHAR(50)   NOT NULL,
    created_by_id BIGINT        NOT NULL,
    company_id    BIGINT        NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    total_amount  NUMERIC(12,2) NOT NULL,
    notes         VARCHAR(500),
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT uq_orders_reference UNIQUE (reference)
);

-- ── order_lines ──
CREATE TABLE IF NOT EXISTS order_lines (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INTEGER       NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    subtotal   NUMERIC(10,2) NOT NULL
);

-- ── sales ──
CREATE TABLE IF NOT EXISTS sales (
    id                       BIGSERIAL PRIMARY KEY,
    reference                VARCHAR(50)   NOT NULL,
    seller_id                BIGINT        NOT NULL,
    company_id               BIGINT        NOT NULL,
    warehouse_id             BIGINT,
    status                   VARCHAR(20)   NOT NULL,
    payment_method           VARCHAR(20)   NOT NULL,
    total_amount             NUMERIC(12,2) NOT NULL,
    total_items              INTEGER       NOT NULL,
    buyer_name               VARCHAR(100),
    buyer_phone              VARCHAR(30),
    notes                    VARCHAR(500),
    cancellation_requested   BOOLEAN       NOT NULL,
    cancellation_reason      VARCHAR(500),
    cancellation_requested_at TIMESTAMP,
    created_at               TIMESTAMP     NOT NULL,
    updated_at               TIMESTAMP     NOT NULL,
    CONSTRAINT uq_sales_reference UNIQUE (reference)
);

-- ── sale_items ──
CREATE TABLE IF NOT EXISTS sale_items (
    id         BIGSERIAL PRIMARY KEY,
    sale_id    BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INTEGER       NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    subtotal   NUMERIC(10,2) NOT NULL
);

-- ── sale_edit_requests ──
CREATE TABLE IF NOT EXISTS sale_edit_requests (
    id              BIGSERIAL PRIMARY KEY,
    sale_id         BIGINT       NOT NULL,
    company_id      BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),
    payment_method  VARCHAR(20),
    buyer_name      VARCHAR(100),
    buyer_phone     VARCHAR(30),
    notes           VARCHAR(500),
    requested_by_id BIGINT       NOT NULL,
    requested_at    TIMESTAMP    NOT NULL,
    reviewed_by_id  BIGINT,
    reviewed_at     TIMESTAMP
);

-- ── sale_edit_request_items ──
CREATE TABLE IF NOT EXISTS sale_edit_request_items (
    id         BIGSERIAL PRIMARY KEY,
    request_id BIGINT  NOT NULL,
    product_id BIGINT  NOT NULL,
    quantity   INTEGER NOT NULL
);

-- ── product_comments ──
CREATE TABLE IF NOT EXISTS product_comments (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    author_id  BIGINT       NOT NULL,
    company_id BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    category   VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

-- ── audit_logs ──
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT,
    actor_email VARCHAR(100) NOT NULL,
    actor_roles VARCHAR(50),
    action      VARCHAR(30)  NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   BIGINT,
    entity_name VARCHAR(200),
    details     VARCHAR(500),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL
);

-- ── warehouses (module optionnel) ──
CREATE TABLE IF NOT EXISTS warehouses (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(200),
    active     BOOLEAN      NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

-- ── product_stock_per_warehouse (module optionnel) ──
CREATE TABLE IF NOT EXISTS product_stock_per_warehouse (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT  NOT NULL,
    product_id   BIGINT  NOT NULL,
    warehouse_id BIGINT  NOT NULL,
    quantity     INTEGER NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    CONSTRAINT uq_stock_per_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT ck_stock_per_warehouse_positive CHECK (quantity >= 0)
);

-- ============================================================
-- 2. Clés étrangères (alignées sur les @JoinColumn des entités)
--    Bloc idempotent : une FK n'est ajoutée que si la colonne n'en a
--    AUCUNE déjà (une base créée par Hibernate porte des FK de noms
--    générés — on ne duplique pas, on ne renomme pas non plus :
--    Hibernate valide la présence de la FK, pas son nom).
-- ============================================================
DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN SELECT * FROM (VALUES
        ('categories',                  'fk_categories_company',            'company_id',     'companies'),
        ('products',                    'fk_products_company',              'company_id',     'companies'),
        ('stock_movements',             'fk_stock_movements_company',       'company_id',     'companies'),
        ('orders',                      'fk_orders_company',                'company_id',     'companies'),
        ('sales',                       'fk_sales_company',                 'company_id',     'companies'),
        ('sale_edit_requests',          'fk_sale_edit_requests_company',    'company_id',     'companies'),
        ('product_comments',            'fk_product_comments_company',      'company_id',     'companies'),
        ('warehouses',                  'fk_warehouses_company',            'company_id',     'companies'),
        ('product_stock_per_warehouse', 'fk_pspw_company',                  'company_id',     'companies'),
        ('products',                    'fk_products_category',             'category_id',    'categories'),
        ('stock_movements',             'fk_stock_movements_product',       'product_id',     'products'),
        ('stock_movements',             'fk_stock_movements_order',         'order_id',       'orders'),
        ('stock_movements',             'fk_stock_movements_performer',     'performed_by_id','users'),
        ('stock_movements',             'fk_stock_movements_warehouse',     'warehouse_id',   'warehouses'),
        ('order_lines',                 'fk_order_lines_order',             'order_id',       'orders'),
        ('order_lines',                 'fk_order_lines_product',           'product_id',     'products'),
        ('sales',                       'fk_sales_seller',                  'seller_id',      'users'),
        ('sales',                       'fk_sales_warehouse',               'warehouse_id',   'warehouses'),
        ('sale_items',                  'fk_sale_items_sale',               'sale_id',        'sales'),
        ('sale_items',                  'fk_sale_items_product',            'product_id',     'products'),
        ('sale_edit_requests',          'fk_sale_edit_requests_sale',       'sale_id',        'sales'),
        ('sale_edit_requests',          'fk_sale_edit_requests_requester',  'requested_by_id','users'),
        ('sale_edit_requests',          'fk_sale_edit_requests_reviewer',   'reviewed_by_id', 'users'),
        ('sale_edit_request_items',     'fk_seri_request',                  'request_id',     'sale_edit_requests'),
        ('sale_edit_request_items',     'fk_seri_product',                  'product_id',     'products'),
        ('product_comments',            'fk_product_comments_product',      'product_id',     'products'),
        ('product_comments',            'fk_product_comments_author',       'author_id',      'users'),
        ('product_stock_per_warehouse', 'fk_pspw_product',                  'product_id',     'products'),
        ('product_stock_per_warehouse', 'fk_pspw_warehouse',                'warehouse_id',   'warehouses')
    ) AS t(tbl, conname, col, reftbl)
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE con.contype = 'f'
              AND rel.relname = fk.tbl
              AND con.conkey @> ARRAY[
                  (SELECT attnum::smallint FROM pg_attribute
                   WHERE attrelid = rel.oid AND attname = fk.col)
              ]::smallint[]
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I (id)',
                fk.tbl, fk.conname, fk.col, fk.reftbl);
        END IF;
    END LOOP;
END $$;

-- Unicité par entreprise (mêmes index que V3/V4) + unicité email
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_company_reference
    ON sales (company_id, reference);
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_company_reference
    ON products (company_id, reference) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_company_name
    ON categories (company_id, name) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouses_company_name
    ON warehouses (company_id, name) WHERE active = TRUE;

-- Index de filtrage par entreprise
CREATE INDEX IF NOT EXISTS idx_products_company             ON products (company_id);
CREATE INDEX IF NOT EXISTS idx_categories_company           ON categories (company_id);
CREATE INDEX IF NOT EXISTS idx_sales_company                ON sales (company_id);
CREATE INDEX IF NOT EXISTS idx_orders_company               ON orders (company_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_company      ON stock_movements (company_id);
CREATE INDEX IF NOT EXISTS idx_users_company                ON users (company_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_company           ON audit_logs (company_id);
CREATE INDEX IF NOT EXISTS idx_product_comments_company     ON product_comments (company_id);
CREATE INDEX IF NOT EXISTS idx_sale_edit_requests_company   ON sale_edit_requests (company_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_company           ON warehouses (company_id);
CREATE INDEX IF NOT EXISTS idx_pspw_company                 ON product_stock_per_warehouse (company_id);
CREATE INDEX IF NOT EXISTS idx_pspw_warehouse               ON product_stock_per_warehouse (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_warehouse    ON stock_movements (warehouse_id);
