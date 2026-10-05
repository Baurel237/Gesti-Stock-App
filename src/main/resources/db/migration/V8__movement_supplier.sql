-- ============================================================
-- V6 : fournisseur sur les mouvements de stock (story 2.2)
--   - supplier_id nullable sur stock_movements (rapprovisionnement tracé)
--   - null = mouvement sans fournisseur (rétrocompatibilité)
-- ============================================================

ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_supplier ON stock_movements (supplier_id);
