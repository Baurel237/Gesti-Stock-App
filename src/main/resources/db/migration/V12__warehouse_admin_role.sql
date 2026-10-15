-- « Direction Générale + boutiques » (1 entrepôt = 1 boutique) :
-- le nouveau rôle WAREHOUSE_ADMIN (administrateur d'entrepôt) est ajouté à
-- la contrainte CHECK de user_roles. Le reste (affectations user_warehouses,
-- stock par entrepôt) existe déjà (V7).

ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_role_check;
ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_check CHECK (role IN
    ('SUPER_ADMIN', 'ADMIN', 'WAREHOUSE_ADMIN', 'MANAGER', 'GESTIONNAIRE', 'SELLER', 'USER', 'VIEWER'));
