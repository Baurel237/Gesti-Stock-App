-- Ajout des champs de demande d'annulation à la table sales
-- Ces champs sont nécessaires pour la fonctionnalité d'annulation des ventes

ALTER TABLE sales ADD COLUMN IF NOT EXISTS cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMP;
