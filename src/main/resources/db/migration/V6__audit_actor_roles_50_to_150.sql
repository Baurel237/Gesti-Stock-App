-- V6 : la colonne audit_logs.actor_roles (VARCHAR(50)) est trop courte.
-- La chaîne CSV des rôles d'un compte multi-rôles (ex. superadmin de démo :
-- ADMIN,GESTIONNAIRE,MANAGER,SELLER,SUPER_ADMIN,USER,VIEWER = 57 caractères)
-- dépasse la limite : l'insert d'audit échoue, marque la transaction
-- rollback-only et fait échouer l'action métier (ex. création d'entrepôt).
-- On élargit à 150 (marge pour de futurs rôles).
ALTER TABLE audit_logs ALTER COLUMN actor_roles TYPE VARCHAR(150);
