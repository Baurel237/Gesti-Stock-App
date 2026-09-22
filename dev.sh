#!/usr/bin/env bash
# Démarre le backend Spring Boot en chargeant les variables de .env.
# Usage : ./dev.sh        (équivalent à mvnw spring-boot:run avec les secrets chargés)
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "❌ Fichier .env introuvable."
  echo "   Copiez le modèle puis renseignez les valeurs :"
  echo "     cp .env.example .env"
  exit 1
fi

set -a
. ./.env
set +a

exec ./mvnw spring-boot:run -DskipTests
