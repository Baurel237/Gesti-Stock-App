-- ============================================================
-- V10 : types de commandes (commande fournisseur + commande client)
--   - order_type  : STANDARD (historique), PURCHASE (fournisseur, entrées
--     de stock à la validation), CLIENT (vendeur : nom client + date de
--     livraison, sorties validées par l'admin)
--   - supplier_id : fournisseur de la commande (PURCHASE uniquement)
--   - client_name / delivery_date : commande CLIENT uniquement
-- Les lignes existantes restent STANDARD (valeur par défaut).
-- ============================================================

ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type    VARCHAR(20)  NOT NULL DEFAULT 'STANDARD';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS supplier_id   BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS client_name   VARCHAR(100);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_date DATE;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id);

CREATE INDEX IF NOT EXISTS idx_orders_supplier ON orders (supplier_id);
CREATE INDEX IF NOT EXISTS idx_orders_type     ON orders (order_type);