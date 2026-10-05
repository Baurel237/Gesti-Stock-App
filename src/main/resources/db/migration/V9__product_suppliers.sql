-- ============================================================
-- V9 : association produits ↔ fournisseurs (stories 2.3/2.4)
--   - table de jointure product_suppliers (many-to-many, un produit
--     peut être approvisionné par plusieurs fournisseurs)
--   - suppression en cascade avec le produit ; le dé-liage du
--     fournisseur est géré applicativement (suppression logique RG-04)
--   - index sur supplier_id : filtre du catalogue par fournisseur (2.4)
-- ============================================================

CREATE TABLE IF NOT EXISTS product_suppliers (
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, supplier_id)
);

CREATE INDEX IF NOT EXISTS idx_product_suppliers_supplier ON product_suppliers (supplier_id);
