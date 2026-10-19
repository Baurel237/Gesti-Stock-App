-- ══════════════════════════════════════════════════════════════════
-- V13 : Suppression du module entrepôts (stock centralisé)
-- ══════════════════════════════════════════════════════════════════
-- L'application fonctionne désormais sur un stock centralisé unique.
-- Toutes les données requises pour ce fonctionnement sont préservées :
--
--   - products.quantity : source de vérité du stock (inchangé, déjà
--     décrémenté par les ventes / mouvements même en mode
--     entrepôts) ;
--   - stock_movements : historique complet des entrées / sorties ;
--   - sales, orders, users : toutes les autres colonnes conservées.
--
-- Éléments supprimés (devenus inutiles) :
--   - table product_stock_per_warehouse (stock par entrepôt) ;
--   - table user_warehouses (jointure utilisateurs ↔ entrepôts) ;
--   - table warehouses ;
--   - colonne warehouse_id sur stock_movements, sales, orders ;
--   - colonne warehouse_enabled sur companies.
-- ══════════════════════════════════════════════════════════════════

-- 1. Tables liées aux entrepôts (produits / compagnies et jointures
--    utilisateurs sont conservées via leurs FK vers les autres tables).
DROP TABLE IF EXISTS product_stock_per_warehouse CASCADE;
DROP TABLE IF EXISTS user_warehouses CASCADE;
DROP TABLE IF EXISTS warehouses CASCADE;

-- 2. Relations entre les objets métier et les entrepôts : la colonne est
--    supprimée seulement si elle existe encore, avec ses FK et index.
DO $$
BEGIN
    -- stock_movements.warehouse_id
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name = 'stock_movements'
                 AND column_name = 'warehouse_id') THEN
        ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS fk_stock_movements_warehouse;
        EXECUTE 'DROP INDEX IF EXISTS idx_stock_movements_warehouse';
        ALTER TABLE stock_movements DROP COLUMN warehouse_id;
    END IF;

    -- sales.warehouse_id
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name = 'sales'
                 AND column_name = 'warehouse_id') THEN
        ALTER TABLE sales DROP CONSTRAINT IF EXISTS fk_sales_warehouse;
        ALTER TABLE sales DROP COLUMN warehouse_id;
    END IF;

    -- orders.warehouse_id
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name = 'orders'
                 AND column_name = 'warehouse_id') THEN
        ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_warehouse;
        EXECUTE 'DROP INDEX IF EXISTS idx_orders_warehouse';
        ALTER TABLE orders DROP COLUMN warehouse_id;
    END IF;

    -- companies.warehouse_enabled
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name = 'companies'
                 AND column_name = 'warehouse_enabled') THEN
        ALTER TABLE companies DROP COLUMN warehouse_enabled;
    END IF;
END $$;

-- 3. Nettoyage des index résiduels (sécurité : base créée par Hibernate
--    ou par des migrations antérieures).
DROP INDEX IF EXISTS idx_warehouses_company;
DROP INDEX IF EXISTS idx_pspw_company;
DROP INDEX IF EXISTS idx_pspw_warehouse;
DROP INDEX IF EXISTS idx_user_warehouses_warehouse;
DROP INDEX IF EXISTS uq_warehouses_company_name;
