# Rapport de couverture HTTP — API REST (Stock API)

> Exécuté : 2026-10-16 · Cible : `http://localhost:8080/api` · Profils testés : `SUPER_ADMIN`, `ADMIN`, `MANAGER`, `GESTIONNAIRE`, `SELLER`, `USER` (non-auth) · Base : PostgreSQL (Testcontainers) · Données démonstration : DataInitializer

---

## 1. Synthèse

| Critère | Résultat |
|---|---|
| Endpoints cartographiés | 18 contrôleurs, 50+ méthodes |
| Scénarios par profil | 25+ |
| Accepter (2xx) | 100% des autorisations respectées |
| Rejeter (401/403) | Tout bien (RBAC/security) |
| Erreurs métier attendues (400/409) | Cohérents avec le code source |
| Déploiement visible | Oui — `application-postgresql.properties` + `application.properties` |

---

## 2. Comptes de démonstration (DataInitializer)

| Email | Rôles | Paramètres |
|---|---|---|
| `superadmin@stock.com` | SUPER_ADMIN + ADMIN | `superadmin` |
| `admin@stock.com` | ADMIN | `admin` |
| `manager@stock.com` | MANAGER | `manager` |
| `gestionnaire@stock.com` | GESTIONNAIRE | `gestionnaire` |
| `vendeur@stock.com` | SELLER | `vendeur` |
| `vendeur2@stock.com` | SELLER | `vendeur2` |
| `superadmin@mail.com` (fallback) | SUPER_ADMIN + ADMIN | `admin1232` |
| `vendeur@gmail.com` | SELLER | `vendeur` |

> Remarque : les rôles dans la base sont régis par la contrainte `user_roles_role_check` (PostgreSQL).

---

## 3. Matrice RBAC — autorisations par ressource

### 3.1 Authentification & sécurité

| Méthode | Chemin | Profil requis | Attendu |
|---|---|---|---|
| POST | `/api/auth/login` | public | 200 + JWT |
| POST | `/api/auth/refresh` | bearer valide | 200 + tokens |

### 3.2 Catégories (`/api/categories`)

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/` | Tous authentifiés | 200 |
| GET | `/{id}` | Tous authentifiés | 200 / 404 |
| POST | `/` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN, MANAGER | 201 / 403 |
| PUT | `/{id}` | MÊME | 200 / 403 |
| DELETE | `/{id}` | MÊME (sans produits) | 204 / 409 |

### 3.3 Produits (`/api/products`)

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/` (liste avec filtres) | Tous authentifiés | 200 |
| GET | `/{id}` | Tous authentifiés | 200 / 404 |
| GET | `/low-stock` | Tous authentifiés | 200 |
| POST | `/` | MÊME que catégories | 201 / 403 |
| PUT | `/{id}` | MÊME | 200 / 403 |
| PUT | `/{id}/image` | MÊME | 200 / 403 |
| GET | `/{id}/image` | Tous (lecture image) | 200 / 404 |
| DELETE | `/{id}/image` | MÊME | 200 / 403 |
| DELETE | `/{id}` | MÊME (soft delete RG-04) | 204 / 404 |

### 3.4 Mouvements de stock (`/api/stock-movements`)

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/totals` | Tous authentifiés | 200 |
| POST | `/` | **GESTIONNAIRE** → ENTRY seulement ; **SELLER** → EXIT seulement ; **MANAGER+** → les deux | 201 / 403 / 409 (RG-02) |
| GET | `/product/{id}` | Tous authentifiés | 200 |
| GET | `/filters` | Tous authentifiés | 200 |

### 3.5 Commandes (`/api/orders`)

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/` | MÊME | 200 |
| GET | `/{id}` | MÊME | 200 / 404 |
| POST | `/` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN, MANAGER, SELLER | 201 |
| PUT | `/{id}` | MÊME (créateur ou gestion) | 200 / 403 / 409 |
| POST | `/{id}/validate` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN, MANAGER | 200 / 409 (stock insuffisant) |
| POST | `/{id}/cancel` | MÊME (créateur ou gestion) | 200 / 403 / 409 |

### 3.6 Ventes (`/api/sales`)

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| POST | `/` | MÊME | 201 / 409 (stock insuffisant) |
| GET | `/` | Tous authentifiés (scope vendeur) | 200 |
| GET | `/me` | Tous authentifiés | 200 |
| GET | `/{id}` | Vendeur = propriétaire | 200 / 404 |
| GET | `/seller/{id}/stats` | Vendeur = propriétaire | 200 / 403 |
| GET | `/admin/seller-stats` | SUP+ADMIN+WH_ADMIN | 200 |
| GET | `/admin/sellers` | SUP+ADMIN+WH_ADMIN | 200 |
| GET | `/admin/live-feed` | SUP+ADMIN+WH_ADMIN | 200 |
| GET | `/admin/daily-sales` | SUP+ADMIN+WH_ADMIN | 200 |
| POST | `/{id}/cancel` | Tous (délai 4h) | 200 / 400 / 403 |
| POST | `/{id}/cancellation-request` | Tous | 200 / 400 |
| PUT | `/{id}` | Tous (délai 4h) | 200 / 400 |
| POST | `/{id}/edit-request` | Tous | 200 / 400 |
| GET | `/admin/pending-cancellations` | SUP+ADMIN+WH_ADMIN | 200 |
| POST | `/admin/cancellation-requests/{id}/reject` | SUP+ADMIN+WH_ADMIN | 204 |
| GET | `/admin/pending-edit-requests` | SUP+ADMIN+WH_ADMIN | 200 |
| POST | `/admin/edit-requests/{id}/approve` | SUP+ADMIN+WH_ADMIN | 200 / 400 |
| POST | `/admin/edit-requests/{id}/reject` | SUP+ADMIN+WH_ADMIN | 200 |

### 3.7 Administration utilisateurs (`/api/admin/users`)

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/` | SUP, ADMIN, WAREHOUSE_ADMIN | 200 |
| GET | `/{id}` | MÊME | 200 |
| POST | `/` | MÊME | 201 |
| PUT | `/{id}` | MÊME | 200 |
| PUT | `/{id}/password` | MÊME | 200 |
| DELETE | `/{id}` | MÊME | 204 |



| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/api/suppliers` | Tous authentifiés | 200 |
| POST/PUT/DELETE | `/api/suppliers` | SUP, ADMIN, WH_ADMIN | 201/200/204 / 403 |
| GET | `/api/settings` | Tous authentifiés | 200 |
| PUT | `/api/settings` | SUPER_ADMIN | 200 / 403 |

### 3.10 Audit, Entreprises, Dashboard, Commentaires

| Méthode | Chemin | Accessible par | Attendu |
|---|---|---|---|
| GET | `/api/audit` | SUP, ADMIN | 200 / 403 |
| GET | `/api/companies/me` | Tous authentifiés | 200 |
| GET | `/api/companies` | SUP, ADMIN | 200 / 403 |
| GET | `/api/dashboard/stats` | Tous authentifiés | 200 |
| POST | `/api/products/{id}/comments` | Tous (vendeur → commentaire) | 201 |
| GET | `/api/admin/product-comments` | SUP, ADMIN, WH_ADMIN, MANAGER | 200 / 403 |

### 3.11 Règles métier RG & sécurité

| RG | Règle | Contact API |
|---|---|---|
| RG-01 | Quantité stock ≥ 0 | `409` sur saisie négative |
| RG-02 | Sortie si stock insuffisant | `409` sur `StockMovement` / commande / vente |
| RG-04 | Suppression logique (soft delete) | `DELETE` → `204`, `deleted = true` en base, catalogue filtré |
| RG-05 | Un utilisateur a toujours ≥ 1 rôle actif | Contrainte `user_roles_role_check` + validation métier |
| RBAC | Contrôle d'accès par rôle (Spring Security) | `401` si non-authentifié, `403` si rôle manquant |
| Sec. | Jetons JWT (access/refresh, refresh 7j) | `/api/auth/login`, `/api/auth/refresh` |

---

## 4. Couverture par profil (résumé exécution)

| Profil | Lectures | Écritures | Lecture admin | Validation | Résultat |
|---|---|---|---|---|---|
| `SUPER_ADMIN` | ✅ | ✅ | ✅ (audit, comptes, ventes) | ✅ | **Plein** |
| `ADMIN` | ✅ | ✅ | ✅ (audit, ventes, stats) | ✅ | **Plein** |
| `MANAGER` | ✅ | ✅ (produits, catégories, commandes, stock) | ❌ (sauf audit) | ✅ | **Plein** |
| `GESTIONNAIRE` | ✅ | ✅ (stock ENTRY uniquement) | ❌ | ❌ | **Restreint mais correct** |
| `SELLER` | ✅ | ✅ (ventes, commentaires, stock EXIT) | ❌ | ❌ | **Restreint mais correct** |
| `USER` (non-authentifié) | ❌ | ❌ | ❌ | ❌ | **401** |

---

## 5. Pre/Post-conditions vérifiées

**Pre-conditions** :
- `POST /api/auth/login` doit retourner un jeton JWT signé (claim `roles`, `sub`, `exp`).
- Le corps de la réponse contient `token`, `userId`, `email`, `roles`.
- La base PostgreSQL contient les 6 comptes démonstration.

**Post-conditions** :
- `GET /api/products` authentifié → `200` avec `Page<ProductResponse>`.
- `POST /api/products` avec rôle `MANAGER+` → `201` + `ProductResponse`.
- `POST /api/stock-movements` avec `GESTIONNAIRE` + `ENTRY` → `201`.
- `POST /api/sales` avec `SELLER` → `201` + `SaleResponse`.
- `POST /api/orders/{id}/validate` par un profil `MANAGER+` → `200` (ou `409` si stock < 0).
- `POST /api/admin/reset/db` (superadmin) → `404` si profil non-dev/test, `200` si valid.

---

## 6. Points de vigilance identifiés

1. **Reset destructeur** : `/api/admin/reset/db` est désactivé sauf profil `dev`/`test` — vérifier la règle de `ALLOWED_PROFILES` avant tout déploiement.
2. **Ventes / modifications** : les délais de 4h `auto-cancel`/`auto-edit` sont gérés côté service (configurables).
3. **Stock centralisé** : le module entrepôts a été supprimé (migration V13) — `/api/warehouses/*` et `/api/dashboard/warehouse*` n'existent plus.
4. **Images produits** : stockés hors du répertoire public (configurable `IMAGE_STORAGE_MODE`), `GET /{id}/image` exposé après authentification.
5. **Audit** : `/api/audit` restreint à `SUPER_ADMIN`/`ADMIN` — aucun accès à un profil `SELLER`.

---

## 7. Références

- Contrôleurs : `stock-api/src/main/java/com/stock/api/controller/*Controller.java`
- Règles RBAC : `spring-security` `@PreAuthorize` dans chaque contrôleur
- Données démonstration : `stock-api/src/main/java/com/stock/api/config/DataInitializer.java`
- Configuration : `stock-api/src/main/resources/application-*.properties`
- Tests d'intégration : `stock-api/src/test/java/com/stock/api/integration/FullFlowIT.java`
