-- ============================================================
-- V5 : fournisseurs + affectation des utilisateurs aux entrepôts
--   - table suppliers (par entreprise, suppression logique)
--   - table user_warehouses (jointure utilisateurs ↔ entrepôts)
-- L'affectation est optionnelle : un utilisateur sans ligne dans
-- user_warehouses conserve la visibilité actuelle (tous les entrepôts).
-- ============================================================

-- ------------------------------------------------------------
-- Fournisseurs (story 2.1)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suppliers (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES companies(id),
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(100),
    phone      VARCHAR(30),
    address    VARCHAR(200),
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_suppliers_company ON suppliers (company_id);

-- Un nom de fournisseur est unique au sein d'une entreprise (parmi les non supprimés)
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_company_name
    ON suppliers (company_id, name) WHERE deleted = FALSE;

-- ------------------------------------------------------------
-- Affectation des utilisateurs aux entrepôts (stories 1.1/1.2/1.3)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_warehouses (
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, warehouse_id)
);

CREATE INDEX IF NOT EXISTS idx_user_warehouses_warehouse ON user_warehouses (warehouse_id);
