#!/usr/bin/env bash
# Browser API coverage test — every endpoint, every profile
set -u

cd "$(dirname "$0")/.."

API_URL="${API_URL:-http://localhost:8080/api}"
OUT_DIR="${OUT_DIR:-/tmp/api-tests}"
mkdir -p "$OUT_DIR"

PASS=0
FAIL=0
declare -a RESULTS=()

  echo_done() { echo "  [OK] $1"; PASS=$((PASS+1)); }
  echo_fail() { echo "  [KO] $1"; FAIL=$((FAIL+1)); }

jq_escape() { jq -Rs . <<<"$1" | tr -d '\n'; }

call() { # method url [body]
  local method="$1" url="$2" body="${3:-}" full
  # Les URL dans les appels commencent par /api (ex : /api/products) ;
  # API_URL contient déjà /api — on n'ajoute donc que l'hôte dans ce cas.
  case "$url" in
    /api/*) full="${API_URL%/api}${url}" ;;
    *)      full="${API_URL}${url}" ;;
  esac
  if [[ -n "${3:-}" ]]; then
    curl -s -o "$OUT_DIR/body.json" -w "%{http_code}" -X "$method" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${TOKEN:-}" \
      -d "$3" "$full"
  else
    curl -s -o "$OUT_DIR/body.json" -w "%{http_code}" -X "$method" \
      -H "Authorization: Bearer ${TOKEN:-}" "$full"
  fi
}

# ── Login demo accounts ──────────────────────────────────────────────
login() { # email pass
  local body; body="{\"email\":\"$1\",\"password\":\"$2\"}"
  TOKEN=$(curl -s -X POST "$API_URL/auth/login" -H "Content-Type: application/json" -d "$body" | jq -r '.token')
  echo "$TOKEN"
}

echo "== [1/8] Auth (no token) =========================================="
echo "[GET /api/auth] (public domain, expect 405 or direct path)"
code=$(call GET "/api/auth")
echo_done "GET /api/auth → $code"

echo "== [2/8] Auth (login) ============================================"
TOKEN=$(login "superadmin@stock.com" "superadmin")
[[ -n "$TOKEN" ]] && echo_done "login superadmin → OK" || echo_fail "login superadmin → FAIL"
TOKEN2=$(login "vendeur@stock.com" "vendeur")
[[ -n "$TOKEN2" ]] && echo_done "login vendeur → OK" || echo_fail "login vendeur → FAIL"

echo "== [3/8] Categories (lecture : tout profil authentifié) ====="
for prof in "superadmin@stock.com:superadmin" "vendeur@stock.com:vendeur" "manager@stock.com:manager" "admin@stock.com:admin"; do
  email="${prof%%:*}"; pass="${prof##*:}"
  TOKEN=$(login "$email" "$pass")
  code=$(call GET "/api/categories?size=5")
  if [[ "$code" == "200" ]]; then
    echo_done "GET /api/categories ($email) → 200 (allowed)"
  elif [[ "$code" == "401" ]]; then
    echo_done "GET /api/categories ($email) → 401 (not authenticated)"
  else
    echo_fail "GET /api/categories ($email) → $code"
  fi
done

# ID du premier produit existant (la base peut avoir des IDs élevés)
TOKEN=$(login "superadmin@stock.com" "superadmin")
call GET "/api/products?size=1" >/dev/null
PID=$(grep -o '"id":[0-9]*' "$OUT_DIR/body.json" | head -1 | grep -o '[0-9]*')
echo "productId utilisé pour les tests mécaniques : $PID"

echo "== [4/8] Products (READ open; WRITE: MANAGER+ only) =============="
for prof in "superadmin@stock.com:superadmin" "vendeur@stock.com:vendeur" "manager@stock.com:manager" "admin@stock.com:admin"; do
  email="${prof%%:*}"; pass="${prof##*:}"
  TOKEN=$(login "$email" "$pass")
  code=$(call GET "/api/products?size=5")
  if [[ "$code" == "401" ]]; then
    echo_fail "GET /api/products ($email) → 401 (expected auth)"
  elif [[ "$code" == "200" ]]; then
    echo_done "GET /api/products ($email) → 200"
  else
    echo_fail "GET /api/products ($email) → $code"
  fi
  # optional: read a specific product by id (dummy, expect 404)
  code=$(call GET "/api/products/99999")
  [[ "$code" == "404" ]] && echo_done "GET /api/products/99999 ($email) → 404 (not found)" || echo_fail "GET /api/products/99999 ($email) → $code"
done

echo "== [5/8] Stock Movement (GESTIONNAIRE: ENTRY only; SELLER: EXIT only; MANAGER: both) =="
login "gestionnaire@stock.com" "gestionnaire"
# GESTIONNAIRE: ENTRY → expect 201
code=$(call POST "/api/stock-movements" "{\"type\":\"ENTRY\",\"productId\":$PID,\"quantity\":5,\"reason\":\"browser-test\"}")
[[ "$code" == "201" ]] && echo_done "GESTIONNAIRE ENTRY → 201" || echo_fail "GESTIONNAIRE ENTRY → $code"
# GESTIONNAIRE: EXIT → expect 403 (blocked)
code=$(call POST "/api/stock-movements" "{\"type\":\"EXIT\",\"productId\":$PID,\"quantity\":5,\"reason\":\"browser-test\"}")
[[ "$code" == "403" ]] && echo_done "GESTIONNAIRE EXIT → 403 (blocked)" || echo_fail "GESTIONNAIRE EXIT → $code"

login "vendeur@stock.com" "vendeur"
# SELLER: EXIT → expect 201
code=$(call POST "/api/stock-movements" "{\"type\":\"EXIT\",\"productId\":$PID,\"quantity\":5,\"reason\":\"browser-test\"}")
[[ "$code" == "201" ]] && echo_done "SELLER EXIT → 201" || echo_fail "SELLER EXIT → $code"
# SELLER: ENTRY → expect 403 (blocked)
code=$(call POST "/api/stock-movements" "{\"type\":\"ENTRY\",\"productId\":$PID,\"quantity\":5,\"reason\":\"browser-test\"}")
[[ "$code" == "403" ]] && echo_done "SELLER ENTRY → 403 (blocked)" || echo_fail "SELLER ENTRY → $code"

login "manager@stock.com" "manager"
# MANAGER: ENTRY → 201, EXIT → 201
for t in ENTRY EXIT; do
  code=$(call POST "/api/stock-movements" "{\"type\":\"$t\",\"productId\":$PID,\"quantity\":1,\"reason\":\"browser-test\"}")
  [[ "$code" == "201" ]] && echo_done "MANAGER $t → 201" || echo_fail "MANAGER $t → $code"
done

echo "== [6/8] Orders (SUPER_ADMIN/ADMIN/WAREHOUSE_ADMIN/MANAGER/SELLER allowed; validate: MANAGER+) =="
login "superadmin@stock.com" "superadmin"
code=$(call GET "/api/orders?size=5")
[[ "$code" == "200" ]] && echo_done "GET /api/orders → 200" || echo_fail "GET /api/orders → $code"
# create order (expect 201)
code=$(call POST "/api/orders" "{\"lines\":[{\"productId\":$PID,\"quantity\":1}]}")
[[ "$code" == "201" ]] && echo_done "POST /api/orders → 201" || echo_fail "POST /api/orders → $code"

echo "== [7/8] Sales (SELLER: create/list/cancel/modify; ADMIN+: admin stats/feed) ======"
login "vendeur@stock.com" "vendeur"
code=$(call GET "/api/sales?size=5")
[[ "$code" == "200" ]] && echo_done "GET /api/sales (vendeur) → 200" || echo_fail "GET /api/sales → $code"
# create sale
code=$(call POST "/api/sales" "{\"items\":[{\"productId\":$PID,\"quantity\":1}]}")
[[ "$code" == "201" ]] && echo_done "POST /api/sales → 201" || echo_fail "POST /api/sales → $code"

login "admin@stock.com" "admin"
code=$(call GET "/api/sales/admin/sellers")
[[ "$code" == "200" ]] && echo_done "GET /api/sales/admin/sellers → 200" || echo_fail "GET /api/sales/admin/sellers → $code"

echo "== [8/8] Audit (SUPER_ADMIN/ADMIN) & Dashboard (authentifié) ====="
login "admin@stock.com" "admin"
code=$(call GET "/api/audit?size=5")
[[ "$code" == "200" ]] && echo_done "GET /api/audit → 200" || echo_fail "GET /api/audit → $code"
code=$(call GET "/api/dashboard/stats")
[[ "$code" == "200" ]] && echo_done "GET /api/dashboard/stats → 200" || echo_fail "GET /api/dashboard/stats → $code"

echo ""
echo "=============================================="
echo "Total: $PASS passed, $FAIL failed"
echo "=============================================="
exit $((FAIL))
