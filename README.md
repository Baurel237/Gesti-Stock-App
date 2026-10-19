# Stock API — gestion de stock centralisée

Backend Spring Boot 3.4 / Java 21 / PostgreSQL (migrations Flyway) pour la
gestion de stock **multi-entreprises**, avec stock **centralisé**.

> **Module entrepôts supprimé (migration V13)** : le fonctionnement optionnel
> « 1 entrepôt = 1 boutique » a été retiré du code et de la base. Les tables
> `warehouses`, `user_warehouses`, `product_stock_per_warehouse` et les
> colonnes `warehouse_id` / `warehouse_enabled` n'existent plus. Tous les
> produits, stocks, ventes, commandes et opérations fonctionnent au niveau de
> l'application, sans sélection d'entrepôt ; le rôle `WAREHOUSE_ADMIN` est
> conservé pour compatibilité (il détient désormais les droits du niveau
> `MANAGER`).

## Démarrage

```bash
cp .env.example .env            # renseigner JWT_SECRET, DB_*, POSTGRES_PASSWORD
docker compose up -d            # PostgreSQL (:5545) + MinIO (:9000, optionnel)
./mvnw spring-boot:run          # API sur http://localhost:8080
```

- **Swagger UI** : http://localhost:8080/swagger-ui/index.html
- **Tests d'intégration** : `mvn verify` — Testcontainers démarre un
  PostgreSQL dédié (mode « Docker détecté ») et rejoue **exactement le schéma
  de production** : Flyway V1→V13 puis Hibernate `ddl-auto=validate`. En local
  sans Testcontainers : `USE_EXTERNAL_PG=true` + variables `E2E_PG_*`
  (voir `src/test/java/com/stock/api/config/PostgresContainerConfig.java`).
- **Tests navigateur** : `bash scripts/browser-api-test.sh` (28 vérifications
  bout en bout, requiert l'API en marche + `jq`).

### Comptes de démonstration (mot de passe = partie avant `@`)

`superadmin@stock.com` · `admin@stock.com` · `manager@stock.com` ·
`gestionnaire@stock.com` · `vendeur@stock.com`

## Authentification

JWT Bearer (`Authorization: Bearer <token>`).

| Méthode | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | `{ email, password }` → `{ token, email, roles, userId }` |
| POST | `/api/auth/refresh` | refresh token → nouveau JWT |

> `POST /api/auth/register` n'existe plus : les comptes sont créés par
> l'administration ou le seed de démonstration.

## Rôles et accès

Hiérarchie : `SUPER_ADMIN > ADMIN > MANAGER/GESTIONNAIRE/SELLER/WAREHOUSE_ADMIN > USER > VIEWER`.
Les rôles héritent des permissions du niveau inférieur.

## Contrats d'API

Tous les endpoints (sauf `/api/auth/*`) exigent un JWT. Nombre : `?page=0&size=20`.

### Produits `/api/products`
| Méthode | Chemin | Rôles |
|---|---|---|
| GET | `/api/products`, `/api/products/{id}`, `/api/products/low-stock` | tout authentifié |
| GET/DELETE | `/api/products/{id}/image` | GET : tout · DELETE : MANAGER+ |
| POST | `/api/products` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN, MANAGER |
| PUT | `/api/products/{id}` | MANAGER+ (idem POST) |
| PUT | `/api/products/{id}/image` (multipart) | MANAGER+ |
| DELETE | `/api/products/{id}` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN (suppression logique) |

Commentaires produits : `POST/GET /api/products/{productId}/comments` ;
modération : `GET /api/admin/product-comments` (MANAGER+).

### Categories `/api/categories`
| Méthode | Chemin | Rôles |
|---|---|---|
| GET | `/api/categories`, `/api/categories/{id}` | tout authentifié |
| POST | `/api/categories` · PUT `/api/categories/{id}` | MANAGER+ |
| DELETE | `/api/categories/{id}` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN |

### Stock — mouvements `/api/stock-movements`
| Méthode | Chemin | Rôles |
|---|---|---|
| POST | `/api/stock-movements` | GESTIONNAIRE : ENTRY uniquement · SELLER : EXIT uniquement · MANAGER+ : les deux (`supplierId` accepté sur ENTRY) |
| GET | `/api/stock-movements/totals` | tout authentifié |
| GET | `/api/stock-movements/product/{productId}` | tout authentifié |
| GET | `/api/stock-movements/filters` (productId, type, supplierId, dates) | tout authentifié |

Règles RG-01/RG-02 : le stock (`products.quantity`) n'est jamais négatif ;
sortie refusée si quantité insuffisante → 409.

### Commandes `/api/orders`
| Méthode | Chemin | Rôles |
|---|---|---|
| GET | `/api/orders`, `/api/orders/{id}` | tout authentifié |
| POST | `/api/orders` | MANAGER+, SELLER |
| PUT | `/api/orders/{id}` | créateur (PENDING) ou MANAGER+ |
| POST | `/api/orders/{id}/validate` | MANAGER+ |
| POST | `/api/orders/{id}/cancel` | MANAGER+, SELLER |

Types (V10) : `PURCHASE` (fournisseur obligatoire → entrées de stock à la
validation), `CLIENT` (`clientName` + `deliveryDate` → sorties), `STANDARD`
(sorties). L'annulation d'une commande validée restitue/retire le stock selon
le type.

### Ventes `/api/sales`
| Méthode | Chemin | Rôles |
|---|---|---|
| POST | `/api/sales` | MANAGER+, SELLER (items, buyerName?, buyerPhone?, paymentMethod?) |
| GET | `/api/sales`, `/api/sales/me`, `/api/sales/{id}` | vendeur : les siennes · MANAGER+ : tout |
| POST | `/api/sales/{id}/cancel` (≤ 4 h auto) | MANAGER+, SELLER |
| PUT | `/api/sales/{id}` (≤ 4 h auto) | MANAGER+, SELLER |
| POST | `/api/sales/{id}/cancellation-request` · `/api/sales/{id}/edit-request` | SELLER (après 4 h) |
| GET/POST | `/api/sales/admin/pending-cancellations`, `/admin/cancellation-requests/{id}/reject`, `/admin/pending-edit-requests`, `/admin/edit-requests/{requestId}/approve`, `/admin/edit-requests/{requestId}/reject` | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN |
| GET | `/api/sales/seller/{sellerId}/stats` · `/admin/seller-stats` · `/admin/sellers` · `/admin/live-feed` · `/admin/daily-sales` | stats : superviseur · admin/* : SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN |

### Fournisseurs `/api/suppliers`
| Méthode | Chemin | Rôles |
|---|---|---|
| GET | `/api/suppliers`, `/api/suppliers/{id}` | tout authentifié |
| POST | `/api/suppliers` · PUT `/api/suppliers/{id}` · DELETE `/api/suppliers/{id}` (logique) | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN |

### Dashboard, entreprises, administration
| Endpoint | Méthode | Rôles |
|---|---|---|
| `/api/dashboard/stats` | GET | tout authentifié (agrégats SQL côté base) |
| `/api/companies/me` | GET | tout authentifié |
| `/api/companies` | GET | SUPER_ADMIN, ADMIN |
| `/api/admin/users` (GET/POST), `/api/admin/users/{id}` (GET/PUT/DELETE), `/{id}/password` (PUT) | CRUD comptes + rôles | SUPER_ADMIN, ADMIN, WAREHOUSE_ADMIN (rôles ADMIN/SUPER_ADMIN : SUPER_ADMIN uniquement) |
| `/api/audit` | GET | SUPER_ADMIN, ADMIN |
| `/api/settings` | GET / PUT | GET : authentifié · PUT : SUPER_ADMIN |
| `/api/fx/rates`, `/api/me/currency` (GET/PUT) | devise affichage | authentifié |
| `/api/admin/reset/db` | POST | SUPER_ADMIN (profils dev/test uniquement) |

> Les statistiques par entrepôt (`/api/dashboard/warehouses/{id}/stats`,
> `/api/dashboard/warehouse-stats`) et `PUT /api/companies/{id}/warehouse-module`
> ont été **supprimées** avec le module entrepôts (V13).

## Migration de base de données

Le schéma est géré intégralement par Flyway (`src/main/resources/db/migration`) :
V1 initialisation, V2–V12 évolutions, **V13 suppression du module entrepôts**.
La migration est appliquée automatiquement au démarrage ; elle est idempotente
et ne supprime aucune donnée métier (ventes, mouvements, commandes, produits).

## Structure du projet

- `src/main/java/com/stock/api` — contrôleurs, services, entités, repositories.
- `src/test/java/com/stock/api` — tests unitaires `*Test` (H2) et
  `*IT` (PostgreSQL, schéma de production).
- `scripts/browser-api-test.sh` — couverture API bout en bout.
- `stock-api.postman_collection.json` — collection Postman.
- `test-report/browser-api-coverage.md` — rapport de couverture API.
