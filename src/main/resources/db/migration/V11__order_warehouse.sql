-- ============================================================
-- V11 : entrepôt / boutique d'origine d'une commande
--   - warehouse_id : entrepôt (ou boutique) rattaché à la commande,
--     résolu automatiquement depuis l'affectation du créateur
--     (vendeur affecté à un entrepôt). Nullable : les commandes
--     passées et les profils non affectés restent sans entrepôt.
--   Servi sur le ticket de commande (nom + localisation).
-- ============================================================

ALTER TABLE orders ADD COLUMN IF NOT EXISTS warehouse_id BIGINT;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

CREATE INDEX IF NOT EXISTS idx_orders_warehouse ON orders (warehouse_id);
