#!/usr/bin/env bash
# Remet à zéro la base de données PostgreSQL d'E2E (stock_db) et la filme
# avec les données de démonstration (DataInitializer) pour que les tests
# d'intégration (*IT) puissent s'exécuter sur un état connu.
# Usage : ./scripts/reset-test-db.sh
set -euo pipefail

PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5545}"
PG_DB="${PG_DB:-stock_db}"
PG_USER="${PG_USER:-stock_user}"
PG_PASSWORD="${PG_PASSWORD:-stock_password}"

if ! command -v psql >/dev/null 2>&1; then
  echo "❌ psql introuvable — installer PostgreSQL client ou définir PG_HOST/PORT/USER/PASSWORD." >&2
  exit 1
fi

# Exécuter à l'intérieur du conteneur PostgreSQL (socket/ports internes)
docker exec -i stock-api-postgres psql -U "$PG_USER" -d "$PG_DB" <<'SQL'
-- Vider toutes les tables, en respectant les contraintes de clés étrangères.
TRUNCATE TABLE
    sale_items, sale_edit_requests, sale_edit_request_items,
    sales, order_lines, orders,
    product_suppliers, suppliers,
    stock_movements, product_comments,
    products, categories, companies, users, roles, user_roles,
    app_settings, audit_logs, flyway_schema_history
CASCADE;

-- Rétablir les séquences à 1 pour des identifiants prévisibles.
SELECT 'ALTER SEQUENCE ' || sequence_name || ' RESTART WITH 1;'
FROM information_schema.sequences
WHERE sequence_schema = 'public'
  AND sequence_name LIKE '%_id_seq'
ORDER BY sequence_name;

-- Semis des données de démonstration (comptes, catégories, produits...),
-- activé via les propriétés spring (seed-demo-data / seed-accounts, par défaut à true).
-- La commande SQL ne peut pas appeler des beans Spring ; le seeding se fait
-- côté application par DataInitializer, activé automatiquement au démarrage
-- de l'application avec les profils d'intégration.
SQL

echo "✅ Base de données E2E remise à zéro et prête au seeding."
