# Spec : passer-commande

- **Statut** : Implemented
- **Auteur** : rolandrm
- **Version** : 0.1.0
- **Liée à** : `service-foundation.md` (socle, erreurs, contrat OpenAPI), `container-image.md` (image, compose, publication), règles `.claude/rules/springboot/` (domain, application, infrastructure, web, api-docs, testing, finance, module-boundaries, wiring)
- **Révisions** : 1 (2026-10-22) : T24 ne lance plus `rollback-check`, qui ne tourne que dans le repo plateforme ; le retour arrière se prouve dans shop-platform à la PR de version 0.2.0 (il en reste 2 avant l'arrêt du cycle de révision)

> **Contrat de shop-api récupéré.** `docs/api/backends/shop-api.openapi.json` (shop-api v1.2.1, commit `5fb52c8`, publié le 2026-10-12, d'après `docs/api/backends/shop-api.source.json`) et la déclaration `backends.shop-api` de `harness.config.json` font partie de cette feature, au même titre que le code : ils sont commités avec elle. La lecture d'un produit ci-dessous est celle de ce fichier.
>
> | Choix | Valeur |
> |---|---|
> | Contrat de shop-api | v1.2.1 : `GET /v1/products/{id}`, devises `EUR`, `USD`, `GBP`, montants à exactement deux décimales |
> | Nom de la spec | `passer-commande` (l'argument ne donnait pas de nom : « Un » n'en est pas un) |
> | Module | `orders`, module riche (le total dépend des lignes : cohérence entre champs) |
> | Produits, prix, stock | propriété de shop-api, lus à la création, jamais stockés comme catalogue |
> | Client | en-tête `X-Customer-Id` (UUID), non vérifié : limite assumée |
> | Base | PostgreSQL 17 (`postgres:17.11-alpine`), base propre à shop-orders |
> | Migrations | Flyway `db/migration/V{n}__*.sql`, appliquées par une image `-migrate` |
> | Identifiants | UUIDv7 produits par un `IdGenerator` injecté |

## Contexte

shop-orders n'a aucune fonctionnalité métier : un client ne peut pas acheter. Les produits, leurs prix et leur stock appartiennent à shop-api. Cette feature permet à un client de passer une commande de produits du catalogue de shop-api, de la conserver telle qu'elle était à l'achat (prix et devise figés) et de la retrouver ensuite, seule ou dans la liste de ses commandes.

## Périmètre

**Inclus** :

- Module `orders` (`domain`, `application`, `infrastructure`, `web`, `wiring`) : agrégat `Order` avec lignes `OrderLine`, `OrderId`, `CustomerId`, `ProductId`
- `POST /v1/orders`, `GET /v1/orders/{orderId}`, `GET /v1/orders` (pagination par curseur)
- Contrat de shop-api et sa déclaration : `docs/api/backends/shop-api.openapi.json`, `docs/api/backends/shop-api.source.json` (écrits par `harness tool contract-pull shop-api`, jamais retouchés à la main) et `backends.shop-api` dans `harness.config.json` (geste humain déjà fait, règle 5)
- Lecture de chaque produit dans shop-api à la création (port `ProductCatalog`, adaptateur `RestClient`), d'après ce contrat ; URL de base `SHOP_API_BASE_URL` (le contrat ne déclare aucun serveur), délais de connexion 2 s et de lecture 3 s, sans nouvelle tentative
- Copie dans la commande du nom, du prix unitaire et de la devise de chaque produit au moment de l'achat
- `sharedkernel` : `Money` (montant `BigDecimal` construit depuis une chaîne + `java.util.Currency`), `IdGenerator` (port) ; nouveau genre `DomainError.Kind.UNAVAILABLE` mappé sur `503` par `GlobalExceptionHandler`
- `platform.config` : `UuidV7IdGenerator`, propriétés `app.shop-api.*` validées
- Persistance : Spring Data JPA, PostgreSQL 17, migration `V1__orders.sql` (tables `orders_order`, `orders_order_line`), Testcontainers PostgreSQL
- Image de migration publiée `ghcr.io/rolandrm/shop-orders-migrate:X.Y.Z` (`publish.extra.migrate`, **geste humain**, règle 5) avec données de démonstration déclarées par `LABEL io.harness.seed` ; étape `migrate` du `Dockerfile`
- `compose.yaml` de développement : service `db` (`postgres:17.11-alpine`, volume nommé, `healthcheck`, port lié à `127.0.0.1:${DB_PORT:-5432}`), service `migrate` à usage unique, `app` qui en dépend ; `.env.example` complété sans valeur réelle de secret
- `docs/runbooks/local.md` : lancer avec la base, joindre shop-api, régénérer le contrat
- Contrat OpenAPI régénéré (`harness gate openapi --write`) et commité

**Exclus** :

- **Authentification et autorisation** (Keycloak, JWT, Spring Security) : spec dédiée, à approuver avant toute recette. `X-Customer-Id` n'est pas vérifié : acceptable en dev, **interdit en recette et en production**
- Réservation ou décrément du stock dans shop-api, paiement, livraison
- Annulation, modification, cycle de vie (statuts) d'une commande
- Idempotence de la création (`Idempotency-Key`) : une double soumission crée deux commandes (voir Risques)
- Cache du catalogue, nouvelle tentative, disjoncteur
- Vue des commandes de tous les clients (back-office)
- Déploiement dans un environnement partagé : repo plateforme

## User stories

### US-001 — Passer une commande

**En tant que** client **je veux** commander des produits **afin de** les acheter

```gherkin
Given shop-api connaît les produits P1 (10.00 EUR) et P2 (2.50 EUR), en stock suffisant
And l'en-tête X-Customer-Id est un UUID valide
When je fais POST /v1/orders avec les lignes (P1, quantité 2) et (P2, quantité 4)
Then la réponse est 201 avec l'en-tête Location /v1/orders/{id}
And la commande contient pour chaque ligne le nom, le prix unitaire, la devise, la quantité et le total de ligne
And le total de la commande vaut 30.00 EUR

Given une requête sans ligne, ou avec plus de 50 lignes, ou avec une quantité hors de 1 à 100, ou avec deux lignes du même produit
When je fais POST /v1/orders
Then la réponse est 400 au format d'erreur commun et aucune commande n'est créée

Given un X-Customer-Id absent ou qui n'est pas un UUID
When je fais POST /v1/orders
Then la réponse est 400 au format d'erreur commun et aucune commande n'est créée
```

### US-002 — Retrouver une commande

**En tant que** client **je veux** consulter une de mes commandes **afin de** vérifier ce que j'ai acheté

```gherkin
Given une commande créée par le client C1
When C1 fait GET /v1/orders/{orderId}
Then la réponse est 200 avec la commande telle qu'à la création

Given une commande créée par le client C1
When le client C2 fait GET /v1/orders/{orderId}
Then la réponse est 404 avec le code ORDER_NOT_FOUND, comme pour un identifiant inconnu

Given un orderId qui n'est pas un UUID
When je fais GET /v1/orders/{orderId}
Then la réponse est 400 au format d'erreur commun
```

### US-003 — Lister mes commandes

**En tant que** client **je veux** la liste de mes commandes **afin de** retrouver celle que je cherche

```gherkin
Given le client C1 a 3 commandes et le client C2 en a 1
When C1 fait GET /v1/orders?limit=2
Then la réponse contient les 2 commandes les plus récentes de C1 et un nextCursor

When C1 refait la requête avec ce cursor
Then la réponse contient la dernière commande de C1 et nextCursor est absent
And aucune commande de C2 n'apparaît

Given un limit supérieur à 100 ou inférieur à 1, ou un cursor illisible
When je fais GET /v1/orders
Then la réponse est 400 au format d'erreur commun
```

### US-004 — Refuser une commande qu'on ne peut pas honorer

**En tant que** client **je veux** un refus clair **afin de** corriger ma commande

```gherkin
Given un productId que shop-api ne connaît pas
When je fais POST /v1/orders
Then la réponse est 400 avec le code PRODUCT_NOT_FOUND et aucune commande n'est créée

Given un produit dont le stock lu dans shop-api est inférieur à la quantité demandée
When je fais POST /v1/orders
Then la réponse est 409 avec le code INSUFFICIENT_STOCK et aucune commande n'est créée

Given deux produits de devises différentes
When je fais POST /v1/orders
Then la réponse est 409 avec le code MIXED_CURRENCIES et aucune commande n'est créée

Given shop-api qui ne répond pas dans le délai, ou répond par une erreur serveur
When je fais POST /v1/orders
Then la réponse est 503 avec le code CATALOG_UNAVAILABLE, sans détail interne, et aucune commande n'est créée
```

### US-005 — Le prix d'une commande ne change plus

**En tant que** client **je veux** que ma commande garde le prix payé **afin que** l'historique soit fiable

```gherkin
Given une commande créée quand P1 valait 10.00 EUR
When shop-api annonce ensuite P1 à 12.00 EUR et que je consulte la commande
Then la ligne affiche toujours 10.00 EUR et le total est inchangé
And la lecture de la commande n'appelle pas shop-api
```

### US-006 — La base et ses migrations se mettent en place avant l'application

**En tant que** développeur **je veux** lancer l'application avec sa base en une commande **afin de** travailler sans procédure à reconstituer

```gherkin
Given un poste avec Docker et un .env copié de .env.example
When je lance docker compose up -d --wait
Then la base est healthy, la migration se termine avec le code 0, puis l'application est healthy

Given l'application démarrée sur une base dont le schéma ne correspond pas aux entités
When elle démarre
Then le démarrage échoue (ddl-auto=validate) et l'application n'applique aucune migration elle-même
```

## Contrat d'API

Format d'erreur commun, inchangé : `{ "statusCode", "code", "message", "details", "traceId" }`. Montants : chaîne validée par `^\d{1,15}\.\d{2}$` (deux décimales, comme shop-api) ; devise : `EUR`, `USD` ou `GBP`. Identifiants : UUID. Dates : `Instant` ISO 8601 UTC.

### `POST /v1/orders`

En-tête obligatoire : `X-Customer-Id` (UUID). Requête :

```json
{
  "items": [
    { "productId": "uuid", "quantity": 2 }
  ]
}
```

`items` : 1 à 50 éléments ; `quantity` : entier de 1 à 100 ; `productId` unique dans la requête. Le client n'envoie aucun prix.

Réponse `201` (en-tête `Location: /v1/orders/{id}`), `OrderResponse` :

```json
{
  "id": "uuid",
  "customerId": "uuid",
  "createdAt": "2026-10-22T10:15:30Z",
  "items": [
    {
      "productId": "uuid",
      "productName": "string",
      "unitPrice": { "amount": "10.00", "currency": "EUR" },
      "quantity": 2,
      "lineTotal": { "amount": "20.00", "currency": "EUR" }
    }
  ],
  "total": { "amount": "30.00", "currency": "EUR" }
}
```

Erreurs : `400` `VALIDATION_ERROR` (corps, en-tête) ou `PRODUCT_NOT_FOUND` ; `409` `INSUFFICIENT_STOCK` ou `MIXED_CURRENCIES` ; `500` `INTERNAL_ERROR` ; `503` `CATALOG_UNAVAILABLE`.

### `GET /v1/orders/{orderId}`

En-tête obligatoire : `X-Customer-Id`. Réponse `200` : `OrderResponse`. Erreurs : `400` `VALIDATION_ERROR`, `404` `ORDER_NOT_FOUND`, `500` `INTERNAL_ERROR`.

### `GET /v1/orders`

En-tête obligatoire : `X-Customer-Id`. Paramètres : `limit` (entier de 1 à 100, défaut 20), `cursor` (chaîne opaque issue de `nextCursor`). Tri : `createdAt` décroissant, puis `id` décroissant. Réponse `200` :

```json
{
  "items": [ { "id": "uuid", "createdAt": "2026-10-22T10:15:30Z", "itemCount": 2, "total": { "amount": "30.00", "currency": "EUR" } } ],
  "nextCursor": "string"
}
```

`nextCursor` est absent sur la dernière page. Erreurs : `400` `VALIDATION_ERROR`, `500` `INTERNAL_ERROR`.

### Lecture d'un produit dans shop-api (appel sortant)

D'après `docs/api/backends/shop-api.openapi.json` (v1.2.1). Une lecture par produit distinct : `GET {SHOP_API_BASE_URL}/v1/products/{id}` (opération « Get a product »), `id` au format UUID ; la route n'accepte aucun paramètre de requête ; le contrat ne déclare aucune authentification, l'appel n'en porte donc pas. `GET /v1/products` (liste) n'est pas utilisé.

Réponse `200`, champs lus (tous obligatoires) :

| Champ shop-api | Type et contrainte du contrat | Usage dans la commande |
|---|---|---|
| `id` | UUID | `productId` de la ligne |
| `name` | chaîne de 1 à 200 caractères | `productName`, copié |
| `price.amount` | chaîne `^\d+\.\d{2}$`, jamais un nombre JSON | `unitPrice.amount`, copié |
| `price.currency` | `EUR`, `USD` ou `GBP` | `unitPrice.currency`, copié |
| `stock` | entier de 0 à 2147483647 ; `0` = en rupture | refus `INSUFFICIENT_STOCK` si `stock` < quantité demandée |

Le contrat déclare `additionalProperties: false` sur cette réponse : l'adaptateur ignore pourtant les champs inconnus qu'une version ultérieure de shop-api ajouterait (évolution compatible), au lieu de refuser la lecture.

Traduction par l'adaptateur :

| Réponse de shop-api | Résultat dans shop-orders |
|---|---|
| `200` | produit lu |
| `404` (code `PRODUCT_NOT_FOUND`) | `PRODUCT_NOT_FOUND`, `400` |
| `400` (id invalide), `500`, autre statut, corps illisible, délai dépassé, connexion refusée | `CATALOG_UNAVAILABLE`, `503` ; l'erreur est journalisée sans corps ni identifiant de client |

Les erreurs de shop-api ont le format `{ statusCode, code, message, details }` (sans `traceId`) ; shop-orders branche sur le statut HTTP, jamais sur `message`, qui est en anglais et peut changer.

## Contraintes non fonctionnelles

- Latence : `GET /v1/orders/{orderId}` et `GET /v1/orders` p95 ≤ 100 ms sur la base locale ; `POST /v1/orders` p95 ≤ 300 ms hors temps de réponse de shop-api, dont le délai de lecture est borné à 3 s par produit
- Sécurité : aucun secret dans le code ni dans le compose commité (`POSTGRES_PASSWORD` référencé, valeur dans `.env` hors git) ; aucun identifiant de client, prix ni corps de requête dans les journaux ; aucune trace de pile dans une réponse ; un client ne lit jamais la commande d'un autre
- Intégrité : montants en `Money`, jamais `double` ni `float` ; `NUMERIC(19,2)` en base ; commande et lignes écrites dans une seule transaction, aucun appel à shop-api dans cette transaction
- Compatibilité de schéma : la migration n'ajoute que des tables, donc la version précédente de l'application tourne sur le nouveau schéma
- Coût : une base PostgreSQL de plus ; au plus 50 lectures shop-api par création

## Plan de test

| Id | Niveau | Scénario | Attendu | Couvre |
|----|--------|----------|---------|--------|
| T1 | unit (domaine, sans Spring) | `Order.place` avec lignes 10.00 × 2 et 2.50 × 4 | total 30.00 EUR ; lignes et totaux de ligne exacts ; événements vides | US-001 |
| T2 | unit (domaine) | `Order.place` avec zéro ligne, quantité 0 ou 101, produit en double, 51 lignes | `Failure` de genre `VALIDATION` pour chacun | US-001 |
| T3 | unit (domaine) | `Order.place` avec des lignes en EUR et en USD | `Failure` `MIXED_CURRENCIES` de genre `CONFLICT` | US-004 |
| T4 | unit (`Money`) | addition, multiplication par un entier, devises différentes, construction depuis `"0.1"` + `"0.2"` | `0.30` exact ; devises différentes refusées ; aucun arrondi implicite | US-001 |
| T5 | unit (cas d'usage `PlaceOrder`, fakes `ProductCatalog` et dépôt) | produits connus et en stock | commande enregistrée avec nom, prix et devise copiés du catalogue ; `Clock` et `IdGenerator` fixes donnent `createdAt` et `id` attendus | US-001, US-005 |
| T6 | unit (cas d'usage) | produit inconnu ; stock insuffisant ; catalogue indisponible | `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `CATALOG_UNAVAILABLE` ; dépôt vide dans les trois cas | US-004 |
| T7 | unit | `UuidV7IdGenerator` avec `Clock` fixe puis avancée | version 7 ; identifiants distincts ; ordre lexicographique suit le temps | US-001 |
| T8 | contrat de port (classe abstraite `ProductCatalogContract`) | fake en unitaire ; `ShopApiProductCatalog` contre `MockRestServiceServer` en intégration, réponses des fixtures `src/test/resources/shop-api/` | produit lu (nom, prix, devise, stock) ; `404` → inconnu ; `400`, `500`, corps illisible, délai dépassé, connexion refusée → indisponible ; champ inconnu dans la réponse `200` ignoré | US-004 |
| T9 | intégration | le chemin appelé par l'adaptateur (`/v1/products/{id}`, méthode `GET`) existe dans `docs/api/backends/shop-api.openapi.json` et les fixtures de T8 portent les champs et statuts que ce fichier déclare (lecture du fichier avec Jackson) | test vert ; échoue si un nouveau `contract-pull` retire ou renomme le chemin, un champ lu ou un statut traduit | US-004 |
| T10 | intégration (`@WebMvcTest` + `MockMvcTester`, cas d'usage fake) | `POST /v1/orders` valide | `201`, `Location`, corps `OrderResponse` conforme | US-001 |
| T11 | intégration (`@WebMvcTest`) | `POST` invalide : sans ligne, 51 lignes, quantité 0 et 101, produit en double, `X-Customer-Id` absent, `X-Customer-Id` non UUID, champ inconnu | `400` `VALIDATION_ERROR` au format commun dans chaque cas | US-001 |
| T12 | intégration (`@WebMvcTest`) | `Failure` de chaque genre du cas d'usage : `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `MIXED_CURRENCIES`, `CATALOG_UNAVAILABLE` | `400`, `409`, `409`, `503` au format commun, sans détail interne | US-004 |
| T13 | intégration (`@DataJpaTest` + Testcontainers PostgreSQL, schéma par Flyway) | enregistrer puis relire une commande de 2 lignes | égalité exacte, montants à 2 décimales, `@Version` incrémentée | US-002, US-005 |
| T14 | intégration (Testcontainers) | commande de C1 lue avec C1 puis avec C2, identifiant inconnu | trouvée pour C1 ; absente pour C2 et pour l'inconnu (même résultat) | US-002 |
| T15 | intégration (Testcontainers) | 3 commandes de C1, 1 de C2 ; pages de 2 | page 1 : 2 plus récentes + curseur ; page 2 : 1 + pas de curseur ; aucune de C2 ; pas de doublon si deux commandes ont le même `createdAt` | US-003 |
| T16 | intégration (`@WebMvcTest`) | `GET /v1/orders/{id}` valide, inconnu, autre client, id non UUID ; `GET /v1/orders` avec `limit` 0, 101, curseur illisible | `200` ; `404` `ORDER_NOT_FOUND` ; `404` ; `400` ; `400` | US-002, US-003 |
| T17 | e2e (`@SpringBootTest`, port aléatoire, Testcontainers, shop-api simulé par `MockRestServiceServer`) | créer une commande, changer le prix annoncé par le faux shop-api, relire la commande | prix et total inchangés ; aucun appel à shop-api pendant la lecture | US-005 |
| T18 | intégration (Testcontainers) | base dont le schéma est vide, application démarrée sans Flyway | démarrage refusé (`ddl-auto=validate`) ; aucune table créée par l'application | US-006 |
| T19 | structure | `harness gate arch --rule spring-domain-pure`, `spring-domain-no-annotation`, `spring-domain-no-clock-random`, `spring-no-cross-module-internals`, `spring-web-no-entity`, `spring-controller-thin`, `spring-jpa-lazy`, `spring-no-query-concat`, `spring-data-repo-package-private`, `spring-amount-not-floating`, `spring-sql-money-not-float`, `spring-value-only-in-config`, `spring-tests-stack`, `spring-gradle-off-stack`, `spring-gradle-versions-in-catalog` | chacune sort en 0 | US-001, US-004, US-006 |
| T20 | structure | `ModularityTests` et `ArchitectureTests` | verts, `orders` déclare ses dépendances autorisées | US-001 |
| T21 | structure | `harness gate arch --prefix platform-` | sort en 0 : compose et Dockerfile sans `:latest`, sans secret en clair | US-006 |
| T22 | outillage | `harness gate openapi` après `--write` ; test de contrat : chaque endpoint figure dans `docs/api/openapi.json` avec descriptions et erreurs | vert ; présence testée, pas le texte caractère pour caractère | US-001, US-002, US-003, US-004 |
| T23 | humain | `harness tool compose-check` | compose valide ; `db` healthy ; `migrate` terminé avec le code 0 avant `app` ; seed appliqué sous l'utilisateur de l'image ; `app` healthy | US-006 |
| T24 | humain | `harness tool image-check --build --requires-env` (image du service et image `-migrate`) | les deux images sont construites, sans secret, utilisateur non root ; le retour arrière n'est pas prouvé ici (`rollback-check` ne tourne que dans le repo plateforme), il l'est dans shop-platform à la PR de version 0.2.0 (N-1 = 0.1.0, sans base) | US-006 |

Les tests « humain » se font à un point d'arrêt du plan : Docker est refusé aux agents. L'humain lance la commande dans un terminal ; l'agent lit le rapport enregistré dans `.claude/state/proofs/`, sans que l'humain ait rien à coller.

Dépendances que le plan de test suppose, toutes gérées par le BOM de Spring Boot 4.1.1 : elles se déclarent dans `gradle/libs.versions.toml` **sans version**, et aucune autre version n'est jamais forcée :

| Artefact | Version gérée par le BOM | Usage |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-data-jpa` | celle de Spring Boot 4.1.1 | exécution (remplace `jakarta.persistence-api` et `spring-data-commons` en test) |
| `org.flywaydb:flyway-core` | 12.4.0 | exécution et tests |
| `org.flywaydb:flyway-database-postgresql` | 12.4.0 | exécution et tests |
| `org.postgresql:postgresql` | 42.7.13 | exécution |
| `org.testcontainers:testcontainers-postgresql` | 2.0.5 | tests (Testcontainers 2.x : les anciens noms `postgresql` et `junit-jupiter` s'arrêtent en 1.21) |
| `org.testcontainers:testcontainers-junit-jupiter` | 2.0.5 | tests |
| `org.springframework.boot:spring-boot-testcontainers` | celle de Spring Boot 4.1.1 | tests (`@ServiceConnection`) |

`MockRestServiceServer` vient de `spring-boot-starter-test`, sans ajout. Le verrou est régénéré par `harness tool deps`.

## Risques

- **Identité non vérifiée** : n'importe quel appelant lit les commandes d'un `X-Customer-Id` qu'il devine ; limite assumée en dev, jamais en recette ni en production tant que la spec d'authentification Keycloak n'est pas livrée
- **Double soumission** : sans `Idempotency-Key`, un double clic ou une nouvelle tentative du front crée deux commandes
- **Survente** : le stock est lu mais pas réservé ; deux commandes simultanées peuvent dépasser le stock réel
- **Dépendance synchrone à shop-api** : sa lenteur ou sa panne bloque les créations (réponse `503` ; les lectures de commandes n'en dépendent pas) ; au pire 50 lectures séquentielles, bornées par les délais
- **Contrat de shop-api qui évolue** : la copie locale est périmée sans nouveau `contract-pull` ; T9 échoue si le chemin, un champ lu ou un statut traduit disparaît, pas si un champ non lu change. Le contrat annonce `info.version` « 1.0.0 » alors que `shop-api.source.json` donne v1.2.1 : la version de référence est celle de `shop-api.source.json`
- **Montants** : `EUR`, `USD` et `GBP` ont deux décimales, comme les prix de shop-api ; une devise à d'autres décimales ajoutée plus tard par shop-api sera refusée par `Money` (aucun arrondi implicite) et fera échouer la création de commande en `CATALOG_UNAVAILABLE` jusqu'à une nouvelle spec
- **Pagination** : curseur sur `(createdAt, id)` ; un index manquant ferait un balayage complet, l'index fait partie de `V1`
- **Seed en production** : les données de démonstration ne doivent tourner que sur `local` (règle de la plateforme)

## Déploiement et retour arrière

- **Ordre de mise en place** : (1) humain, **déjà fait** : `backends.shop-api` déclaré dans `harness.config.json` (règle 5), `harness tool contract-pull shop-api` lancé (écrit `docs/api/backends/shop-api.openapi.json` et `shop-api.source.json`, v1.2.1) ; reste à déclarer `publish.extra.migrate` ; (2) base PostgreSQL 17 propre à shop-orders ; (3) image `shop-orders-migrate:X.Y.Z` (migration `V1`) ; (4) variables d'environnement de l'application : `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SHOP_API_BASE_URL`, `APP_SERVICE_NAME` (démarrage refusé si l'une manque) ; (5) image du service `shop-orders:X.Y.Z`, à la même version que la migration
- **Compatibilité** : la migration ajoute deux tables et leurs index, sans toucher à rien d'existant ; la version précédente du service (sans base) tourne sur le nouveau schéma. Une migration appliquée n'est jamais modifiée : toute correction est une migration `V2`
- **Retour arrière** : redéployer l'image du service précédente ; les tables restent en place, inutilisées, sans effet. Perdu : aucune commande n'est lisible tant que la version précédente tourne ; les commandes écrites entre-temps restent en base et reviennent au redéploiement. La suppression des tables, si elle est voulue, est une migration ultérieure décidée par l'humain
- **Vérification après déploiement** : `GET /actuator/health` répond `200` ; `POST /v1/orders` avec un produit réel de shop-api répond `201`, puis `GET /v1/orders/{id}` renvoie la même commande ; `GET /docs/openapi.json` contient les trois chemins `/v1/orders`
