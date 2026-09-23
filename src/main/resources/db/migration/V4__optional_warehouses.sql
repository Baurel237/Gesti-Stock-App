-- ============================================================
-- V4 : entrepôts OPTIONNELS par entreprise
--   - table warehouses (entreprise avec warehouse_enabled = true uniquement)
--   - stock par entrepôt : product_stock_per_warehouse
--   - warehouse_id nullable sur stock_movements (null = mode stock simple)
--   - warehouse_id nullable sur sales (entrepôt de la vente, si module actif)
-- Règle : une entreprise SANS entrepôts garde le stock simple existant :
--   product.quantity reste la source de vérité, warehouse_id reste NULL.
-- ============================================================

CREATE TABLE IF NOT EXISTS warehouses (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES companies(id),
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(200),
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_warehouses_company ON warehouses (company_id);

-- Un nom d'entrepôt est unique au sein d'une entreprise (parmi les actifs)
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouses_company_name
    ON warehouses (company_id, name) WHERE active = TRUE;

-- Stock par entrepôt : une ligne par (produit, entrepôt)
CREATE TABLE IF NOT EXISTS product_stock_per_warehouse (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT      NOT NULL REFERENCES companies(id),
    product_id   BIGINT      NOT NULL REFERENCES products(id),
    warehouse_id BIGINT      NOT NULL REFERENCES warehouses(id),
    quantity     INTEGER     NOT NULL DEFAULT 0,
    created_at   TIMESTAMP   NOT NULL,
    updated_at   TIMESTAMP   NOT NULL,
    CONSTRAINT uq_stock_per_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT ck_stock_per_warehouse_positive CHECK (quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pspw_company  ON product_stock_per_warehouse (company_id);
CREATE INDEX IF NOT EXISTS idx_pspw_warehouse ON product_stock_per_warehouse (warehouse_id);

-- Mouvements : entrepôt concerné (NULL en mode stock simple)
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS warehouse_id BIGINT REFERENCES warehouses(id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_warehouse ON stock_movements (warehouse_id);

-- Ventes : entrepôt de décrémentation (NULL en mode stock simple)
ALTER TABLE sales ADD COLUMN IF NOT EXISTS warehouse_id BIGINT REFERENCES warehouses(id);
