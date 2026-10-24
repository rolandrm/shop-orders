# Plan : passer-commande

- **Statut** : COMPLETED
- **Taille** : L
- **Durée attendue** : à mesurer (aucun cycle de cette taille sur ce poste ; `.claude/state/cycle-times.json` ne contient que 2 cycles M, médiane 105 min)
- **Points d'arrêt** :
  - après l'étape 5 (migration `V1__orders.sql` écrite et première exécution contre un PostgreSQL réel par Testcontainers : relire le schéma, types `NUMERIC(19,2)` et index de pagination, avant que l'image `-migrate` ne le fige ; une migration appliquée ne se modifie plus)
  - après l'étape 8 (gestes humains : `publish.extra.migrate` dans `harness.config.json`, règle 5 ; `harness ci --force`, règle 4 ; preuves Docker `harness tool compose-check` et `harness tool image-check --build --requires-env`, refusées aux agents ; après la révision de la décision 16, l'humain relance ces deux preuves)
  **Note** : l'humain approuve ce plan en tapant `/harness:ship docs/specs/passer-commande.md --continue` ; il passe `COMPLETED` sur preuve en fin de cycle (toutes les cases cochées, `harness verify` vert).

- **Spec** : `docs/specs/passer-commande.md` (Approved, v0.1.0, révision 1 : T24 limité à `image-check`)
- **Fichiers déjà présents, part de la feature** (à commiter avec elle, jamais réécrits ni retouchés par une étape) : `docs/api/backends/shop-api.openapi.json`, `docs/api/backends/shop-api.source.json`, la déclaration `backends.shop-api` de `harness.config.json`
- **Révision du plan (étape 5)** : `RequiredEnvironmentIT` échouait pour `SPRING_DATASOURCE_*` retirées (Boot laisse le placeholder littéral ; l'erreur Hikari/Hibernate ne nomme pas la variable). Décision humaine 14 ajoutée ; l'étape 5 gagne des fichiers et des critères, ses cases déjà cochées sont conservées.
- **Révision du plan (point d'arrêt de l'étape 5)** : à la relecture de `V1__orders.sql`, l'humain demande des contraintes nommées en défense en profondeur (décision 15), toujours dans `V1` avant toute publication (aucune `V2`). Après trois passes, l'état final compte **8 contraintes nommées** : ajout de `ck_orders_order_line_line_total` (`line_total_amount = unit_price_amount * quantity`) et retrait de `ck_orders_order_line_line_total_amount` (`line_total_amount >= 0`), qui se déduit des autres. L'étape 5 gagne un test de schéma, l'écriture de `line_number` à partir de 1 et des critères ; ses cases déjà cochées sont conservées telles quelles (elles décrivent les états intermédiaires), et l'état final est prouvé par des cases nouvelles.
- **Révision du plan (point d'arrêt de l'étape 8)** : `harness tool compose-check` est rouge : le conteneur `migrate` applique la migration (« Successfully applied 1 migration », Hibernate valide, « Started Application ») puis ne s'arrête jamais, donc `app` (`depends_on: migrate: service_completed_successfully`) ne démarre pas. Décision humaine 16 : un exécuteur de sortie en profil `migrate`. Ajout à l'étape 8 (bloc « Révision, décision 16 ») ; aucune case cochée ne devient fausse (`MigrateProfileIT` et `SeedIT` ne prouvaient pas l'arrêt, ils restent vrais).

## Questions à l'humain

aucune (les 13 questions ont reçu leur réponse, reportée ci-dessous et dans les étapes ; les décisions 14, 15 et 16 viennent des révisions aux points d'arrêt)

## Décisions (réponses de l'humain)

1. **Flyway sous Spring Boot 4** : oui. `org.springframework.boot:spring-boot-starter-flyway` ajouté, sans version (BOM), en plus des artefacts de la spec (étape 1).
2. **`@DataJpaTest` sous Spring Boot 4** : oui. `org.springframework.boot:spring-boot-starter-data-jpa-test` ajouté à la suite `integrationTest`, sans version (BOM) (étape 1).
3. **Image `-migrate`** : oui. Étape `migrate` du `Dockerfile` sur la JRE figée `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24`, même jar, profil `migrate` (`spring.main.web-application-type: none`, `spring.flyway.enabled: true`) : Flyway migre, Hibernate valide (`ddl-auto=validate`), le processus se termine en 0 (arrêt garanti par la décision 16). Le document de base a `spring.flyway.enabled: false` (étapes 3, 5, 8).
4. **Variables du conteneur de migration** : oui. Le profil `migrate` fixe `app.service-name: shop-orders-migrate` et `app.shop-api.base-url: http://shop-api.invalid` (jamais appelée) ; l'image `-migrate` n'exige que `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (étape 5).
5. **Seed** : oui. Profil `seed` ajouté à `migrate`, emplacement `classpath:db/seed`, migration répétable `R__demo_orders.sql` idempotente (identifiants fixes, `ON CONFLICT DO NOTHING`, 2 commandes d'un client de démonstration), `spring.flyway.ignore-migration-patterns: "*:missing"` dans le profil `migrate` ; `LABEL io.harness.seed="java -jar /app/app.jar --spring.profiles.active=migrate,seed"`. Les produits cités par le seed peuvent être absents de shop-api : sans effet, la lecture d'une commande n'appelle pas shop-api (étape 8).
6. **Rejets de requête → `VALIDATION_ERROR`** : oui, limité aux rejets de requête : en-tête manquant (`MissingRequestHeaderException`), UUID illisible (`MethodArgumentTypeMismatchException`), champ JSON inconnu ou corps illisible (`HttpMessageNotReadableException`), paramètre hors bornes (`HandlerMethodValidationException`) → 400 `VALIDATION_ERROR`. Les autres exceptions Spring qui portent un statut gardent le comportement du socle : une route inconnue reste 404 `NOT_FOUND` (étape 3).
7. **Délais prouvés** : oui. Test contre un serveur local du JDK (`com.sun.net.httpserver.HttpServer`) en plus de `MockRestServiceServer` (étape 6).
8. **`RequiredEnvironmentTest`** : oui. Réécrit sans base (étape 3) ; démarrage complet et retrait de chaque variable dans `RequiredEnvironmentIT` (étape 5).
9. **`itemCount`** : nombre de lignes de la commande.
10. **T13, `@Version`** : version initialisée à 0 à l'insertion et relue à l'identique.
11. **`SHOP_API_BASE_URL` du compose** : pas de valeur par défaut. `compose.yaml` : `SHOP_API_BASE_URL: ${SHOP_API_BASE_URL:?SHOP_API_BASE_URL manquante}`. `.env.example` : `SHOP_API_BASE_URL=http://host.docker.internal:8081`, précédé d'un commentaire (shop-api lancé par son propre compose de développement, port 8081 ; à adapter). `extra_hosts: ["host.docker.internal:host-gateway"]` sur `app` seulement parce que Docker Engine sous Linux ne définit pas `host.docker.internal` (Docker Desktop le fait) ; cette raison est écrite en commentaire dans `compose.yaml` et dans le runbook (étape 8).
12. **Workflow CI** : oui. `harness ci --force` lancé par l'humain au point d'arrêt de l'étape 8 (règle 4).
13. **Retour arrière** : non dans ce repo. `rollback-check` ne tourne que dans le repo plateforme ; T24 (spec, révision 1) se limite à `harness tool image-check --build --requires-env` (image du service et image `-migrate`). Le retour arrière se prouve dans shop-platform à la PR de version 0.2.0 (voir « Hors plan »).
14. **Variables obligatoires : une seule liste, par profil (option 1, révision de l'étape 5)**. Un `EnvironmentPostProcessor` de `platform.config` vérifie au démarrage, avant toute création de bean, que chaque variable obligatoire a une valeur non vide dans l'`Environment` (variable d'environnement, propriété système ou argument). La liste est déclarée **à un seul endroit** (`RequiredVariables`, `platform.config`), une ligne par variable avec les profils où elle s'applique : sans profil `migrate`, les 5 variables (`APP_SERVICE_NAME`, `SHOP_API_BASE_URL`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) ; avec le profil `migrate`, seulement les 3 `SPRING_DATASOURCE_*` (décision 4). Le message d'échec nomme chaque variable manquante, jamais une valeur. La prochaine variable n'ajoute qu'une ligne à cette liste. Justification : Boot laisse un placeholder non résolu tel quel dans les propriétés liées, et l'échec de Hikari/Hibernate qui en résulte ne nomme pas la variable ; vérifier tôt, à un seul endroit, donne un message unique et exploitable (et que `image-check --requires-env` lit dans le journal), sans dupliquer la liste entre YAML, propriétés et compose. `RequiredEnvironmentTest` (étape 3, sans base, au niveau de `application.yml`) n'est pas modifié : il prouve une autre couche (les placeholders du YAML), et ses cases cochées restent vraies ; les tests gardent leur propre liste attendue, écrite en clair (l'oracle ne lit pas la liste de production).
15. **Contraintes de schéma dans `V1`, modifiée avant publication (révisions au point d'arrêt de l'étape 5)**. `V1__orders.sql` porte exactement **8 contraintes nommées** : sur `orders_order`, `ck_orders_order_item_count CHECK (item_count BETWEEN 1 AND 50)`, `ck_orders_order_total_amount CHECK (total_amount >= 0)`, `ck_orders_order_currency CHECK (currency ~ '^[A-Z]{3}$')` ; sur `orders_order_line`, `ck_orders_order_line_line_number CHECK (line_number >= 1)`, `ck_orders_order_line_quantity CHECK (quantity BETWEEN 1 AND 100)`, `ck_orders_order_line_unit_price_amount CHECK (unit_price_amount >= 0)`, `ck_orders_order_line_line_total CHECK (line_total_amount = unit_price_amount * quantity)`, `uq_orders_order_line_order_product UNIQUE (order_id, product_id)`. Le reste du schéma est validé tel quel. Justification : défense en profondeur, la base refuse ce que le domaine refuse déjà (bornes de quantité et de lignes, produit unique, prix et total positifs), même pour une écriture qui contournerait l'application (seed, correction manuelle, futur module) ; `ck_orders_order_line_line_total` garantit en base la cohérence entre champs d'une ligne (calcul exact en `NUMERIC`). **Retrait de `ck_orders_order_line_line_total_amount` (`line_total_amount >= 0`)** : elle est la conséquence de l'égalité `line_total_amount = unit_price_amount * quantity`, du prix `>= 0` et de `quantity >= 1` ; une écriture ne pouvait donc jamais la violer sans violer aussi l'une des autres, ce qui empêchait d'en tester la violation seule. On garde `ck_orders_order_line_unit_price_amount` (règle d'origine : un prix n'est jamais négatif) et `ck_orders_order_line_line_total`. Ainsi chaque contrainte restante a une violation qui n'enfreint qu'elle. On **modifie `V1`** au lieu d'ajouter une `V2` parce que `V1` n'a encore été appliquée sur aucune base durable : ni image `-migrate` publiée, ni environnement partagé ; seules les bases jetables de Testcontainers l'ont vue. La règle « une migration appliquée ne se modifie jamais » vaut à partir de la première publication (étape 8 et premier tag) ; une base de développement locale qui l'aurait déjà appliquée se recrée (`docker compose down -v`). Conséquence : Hibernate numérote un `@OrderColumn` à partir de 0 par défaut, ce qui violerait `ck_orders_order_line_line_number` ; l'entité écrit `line_number` à partir de 1 (`@ListIndexBase(1)` de Hibernate, présence et paquet vérifiés dans la version installée). Le cas qui le prouve (`lineNumbersStartAtOne`) reste dans `JpaOrderRepositoryContractIT`, propre à JPA : la suite partagée `OrderRepositoryContract` est intacte (mêmes scénarios pour le fake et JPA).
16. **Arrêt du processus de migration (révision au point d'arrêt de l'étape 8)**. En profil `migrate` seulement, un exécuteur `MigrateExitRunner` (`ApplicationRunner`, `platform.config`, `@Profile("migrate")`) s'exécute une fois le contexte démarré (Flyway appliqué, Hibernate validé) et termine le processus avec `System.exit(SpringApplication.exit(context))`, donc le code 0 sur succès ; un échec au démarrage sort déjà en code non nul, sans passer par lui. Il s'applique aussi aux profils `migrate,seed` (commande `io.harness.seed`). **Cause racine non identifiée** : un thread non démon retient la JVM après le démarrage sans serveur web ; l'exécuteur de sortie est le correctif décidé par l'humain ; si le generator découvre la cause racine (thread nommé dans un vidage de threads, par exemple), il la signale sans changer de correctif. **Tests dans la JVM du worker Gradle** : la sortie est désactivable par la propriété `app.migrate.exit-on-completion` (vraie par défaut : `@ConditionalOnProperty(..., matchIfMissing = true)` sur l'exécuteur, valeur non écrite dans le `application.yml` de production) ; `src/integrationTest/resources/config/application.yml` la met à `false`, pour que `MigrateProfileIT`, `SeedIT` et tout test qui active `migrate` dans la JVM de test ne fassent jamais sortir le worker. Choisi plutôt qu'une action de sortie injectée comme bean : ces tests lancent l'application par `SpringApplication`, sans mécanisme simple de remplacement de bean, alors qu'une propriété de configuration de test les couvre tous sans modifier leur code (si l'un d'eux ignore `classpath:/config/`, il reçoit l'argument `--app.migrate.exit-on-completion=false`). **Preuve de l'arrêt** : `MigrateProcessExitIT` lance l'application dans un **processus séparé** : l'exécutable `java` de la JVM de test (`ProcessHandle.current().info().command()` ou `java.home`), le classpath de la JVM de test (`java.class.path`) et la classe `com.shop.orders.Application`. Choisi plutôt que le jar `bootJar` : aucune dépendance à une tâche Gradle ni à un artefact construit avant `integrationTest`, et les mêmes classes que le reste de la suite. Le processus reçoit `--spring.profiles.active=migrate` et `--spring.config.location=classpath:/application.yml` (seule la configuration de production, donc la sortie activée par défaut ; la configuration de test qui la désactive n'est pas lue), un environnement sans `APP_SERVICE_NAME` ni `SHOP_API_BASE_URL` et avec les seules `SPRING_DATASOURCE_*` du conteneur PostgreSQL Testcontainers, et une sortie redirigée vers un fichier sous `build/` (jamais un tube non lu, qui bloquerait le processus).

## Décisions de conception (sans question)

- **Paquets** : module `com.shop.orders.orders` (`domain`, `application`, `infrastructure`, `web`, `wiring`, `package-info.java` avec `@ApplicationModule(allowedDependencies = …)` + `@NullMarked`). Tests sous `src/test/java/com/shop/orders/orders/` (fakes dans `…/orders/fakes/`) et `src/integrationTest/java/com/shop/orders/orders/`.
- **Suites** : Testcontainers et les dépendances d'intégration ne sont déclarées que pour `integrationTest` ; la suite `integrationTest` voit les classes et ressources de `src/test` (classes de contrat abstraites, fakes, fixtures `src/test/resources/shop-api/`). La suite `test` ne démarre aucune base : ses contextes Spring (`HealthEndpointTest`, `DocsUiDisabledTest`, `OpenApiExportTest`) excluent les auto-configurations DataSource, JPA et Flyway par `src/test/resources/config/application.yml` (noms de classes lus dans les jars Boot 4.1.1 installés) ; le port `OrderRepository` y est remplacé dès qu'il a un adaptateur (étape 5). `openapiExport` reste sans serveur ni base.
- **Transaction** : la commande et ses lignes forment un seul agrégat, écrit par un seul `save` de Spring Data (transactionnel) dans l'adaptateur ; les lectures de shop-api précèdent cet appel. Pas de `TransactionRunner` dans cette feature.
- **Lignes** : `@ElementCollection` de lignes `@Embeddable` dans `orders_order_line`, `@OrderColumn(name = "line_number")` numéroté à partir de 1 (décision 15), clé `(order_id, line_number)` ; chargées par `@EntityGraph` pour la lecture d'une commande ; la liste se lit par projection `record` (sans charger les lignes).
- **Schéma `V1`** : `orders_order(id uuid PK, customer_id uuid, created_at timestamptz, currency char(3), total_amount numeric(19,2), item_count int, version bigint)`, index `(customer_id, created_at DESC, id DESC)` ; `orders_order_line(order_id uuid FK, line_number int, product_id uuid, product_name varchar(200), unit_price_amount numeric(19,2), quantity int, line_total_amount numeric(19,2))` ; plus les 8 contraintes nommées de la décision 15.
- **Curseur** : `(createdAt, id)` encodé en base64url par le `web` (opaque) ; décodé en type applicatif ; illisible → `Failure` `VALIDATION` `VALIDATION_ERROR` → 400. Lecture de `limit + 1` lignes pour savoir s'il reste une page.
- **Client de shop-api** : `RestClient` déclaré dans `orders/wiring/OrdersWiring` (URL et délais lus dans `ShopApiProperties`, fabrique `JdkClientHttpRequestFactory` de `spring-web`, sans dépendance ajoutée, API vérifiée dans la version installée), injecté dans l'adaptateur `ShopApiProductCatalog` (`orders.infrastructure`, package-private). Les tests construisent l'adaptateur avec un `RestClient` lié à `MockRestServiceServer`.
- **Erreurs du domaine** : `ProductNotFound` → `VALIDATION`/`PRODUCT_NOT_FOUND` ; `InsufficientStock` → `CONFLICT` ; `MixedCurrencies` → `CONFLICT` ; `CatalogUnavailable` → `UNAVAILABLE`/`CATALOG_UNAVAILABLE` (503) ; `OrderNotFound` → `NOT_FOUND`/`ORDER_NOT_FOUND`. Commande d'un autre client = même `Failure` qu'un identifiant inconnu.
- **`.env.example`** : `POSTGRES_PASSWORD=change-me-local` (valeur de démonstration, jamais réelle), aucune autre valeur secrète.

## Étapes

### Étape 1 : déclaration des dépendances et de la suite `integrationTest`

Fichiers :
- `gradle/libs.versions.toml` : ajout, **sans version** (BOM Spring Boot 4.1.1), de `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-core`, `flyway-database-postgresql`, `postgresql`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `spring-boot-testcontainers`, `spring-boot-starter-data-jpa-test` (décisions 1 et 2) ; retrait des alias `jakarta-persistence-api` et `spring-data-commons` (remplacés par le starter JPA, spec)
- `build.gradle.kts` : `implementation` du starter JPA, du starter Flyway et de `flyway-core` ; `runtimeOnly` de `postgresql` et `flyway-database-postgresql` ; retrait des `testImplementation` `jakarta.persistence-api` et `spring-data-commons` ; suite `integrationTest` : classes et ressources de `src/test` visibles, dépendances de `testImplementation`/`testRuntimeOnly` héritées, plus `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `spring-boot-testcontainers` et `spring-boot-starter-data-jpa-test`

Critères :
- [x] `harness gate arch --rule spring-gradle-versions-in-catalog` sort en 0
- [x] `harness gate arch --rule spring-gradle-off-stack` sort en 0
- [x] Les alias ajoutés dans `gradle/libs.versions.toml` n'ont ni `version` ni `version.ref` ; les entrées de `[versions]` gardent leurs valeurs
- [x] Aucune dépendance Testcontainers n'est déclarée pour `testImplementation` (seulement pour la suite `integrationTest`)

Pas de compilation ni de test à cette étape : le verrou (`gradle.lockfile`) ne connaît pas encore les nouvelles dépendances, l'étape 2 l'écrit.

### Étape 2 : Dépendances

Sans fichier ni generator : l'orchestrateur lance `harness tool deps` (accord du développeur).

Critères :
- [x] `harness tool deps` finit par « ✅ Tous les contrôles passent » ; `gradle.lockfile` liste `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-core`, `postgresql` et `testcontainers-postgresql`

### Étape 3 : socle partagé (`Money`, `IdGenerator`, `UNAVAILABLE`, rejets de requête, propriétés shop-api) et suite `test` sans base (US-001, US-004, T4, T7)

Fichiers :
- `src/main/java/com/shop/orders/sharedkernel/Money.java` (nouveau) : `BigDecimal` construit depuis une chaîne + `java.util.Currency`, échelle = décimales de la devise, `add`, `multiply(int)`, devises différentes refusées, `RoundingMode.UNNECESSARY`, comparaison par `compareTo`
- `src/main/java/com/shop/orders/sharedkernel/port/IdGenerator.java` (nouveau, port) et `port/package-info.java` (`@NullMarked`)
- `src/main/java/com/shop/orders/sharedkernel/DomainError.java` : genre `UNAVAILABLE`
- `src/main/java/com/shop/orders/platform/web/GlobalExceptionHandler.java` : `UNAVAILABLE` → 503 ; rejets de requête de la décision 6 → 400 `VALIDATION_ERROR`, message fixe, sans détail interne ; tout le reste inchangé (route inconnue → 404 `NOT_FOUND`)
- `src/main/java/com/shop/orders/platform/config/UuidV7IdGenerator.java` (nouveau, `@Component`, `Clock` injecté) ; `ShopApiProperties.java` (nouveau, `@ConfigurationProperties("app.shop-api")` + `@Validated` : `baseUrl` obligatoire dont le message nomme `SHOP_API_BASE_URL`, `connectTimeout` 2 s, `readTimeout` 3 s)
- `src/main/resources/application.yml` : `app.shop-api.base-url: ${SHOP_API_BASE_URL:}`, délais, `spring.datasource.url/username/password` sur `SPRING_DATASOURCE_*` sans valeur par défaut, `spring.flyway.enabled: false` dans le document de base (décision 3)
- `src/test/resources/config/application.yml` : exclusion des auto-configurations DataSource, JPA, Flyway ; `app.shop-api.base-url` de test
- `src/test/java/errorfixture/ErrorFixtureController.java` : routes `UNAVAILABLE`, en-tête obligatoire, paramètre UUID, paramètre borné (`@Validated`)
- Tests : `src/test/java/com/shop/orders/sharedkernel/MoneyTest.java`, `src/test/java/com/shop/orders/platform/config/UuidV7IdGeneratorTest.java`, `ShopApiPropertiesTest.java` (nouveaux) ; `src/test/java/com/shop/orders/platform/web/GlobalExceptionHandlerTest.java` (cas ajoutés) ; `src/test/java/com/shop/orders/RequiredEnvironmentTest.java` (réécrit sans base, décision 8)

Critères :
- [x] T4 passe (`MoneyTest`) : `"0.1"` + `"0.2"` = `0.30` exact ; `10.00` × 2 = `20.00` ; addition EUR + USD refusée ; un montant à 3 décimales en EUR refusé (aucun arrondi implicite)
- [x] T7 passe (`UuidV7IdGeneratorTest`) : version 7, variante RFC 9562 ; 1 000 identifiants distincts ; avec une `Clock` fixe puis avancée d'1 ms, l'ordre lexicographique des chaînes suit le temps
- [x] `GlobalExceptionHandlerTest` passe : `UNAVAILABLE` → 503 au format commun (5 champs) ; en-tête absent, UUID illisible, champ JSON inconnu, paramètre hors bornes → 400 `VALIDATION_ERROR` ; les 6 tests existants passent sans modification (dont `unknownRouteGives404InCommonFormat` : route inconnue → 404 `NOT_FOUND`)
- [x] `ShopApiPropertiesTest` passe : sans `app.shop-api.base-url`, le contexte échoue et la trace contient `SHOP_API_BASE_URL` ; avec, `connectTimeout` = 2 s et `readTimeout` = 3 s
- [x] `RequiredEnvironmentTest` passe : sur le vrai `application.yml`, l'absence de chacune des 5 variables (`APP_SERVICE_NAME`, `SHOP_API_BASE_URL`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) produit une erreur qui la nomme ; toutes fournies, les propriétés se lient
- [x] `HealthEndpointTest`, `DocsUiDisabledTest`, `OpenApiExportTest`, `AppPropertiesTest`, `ResultTest`, `RequestIdFilterTest`, `ModularityTests` et `ArchitectureTests` passent, sans base de données
- [x] `harness gate arch --rule spring-sharedkernel-standalone`, `--rule spring-amount-not-floating`, `--rule spring-value-only-in-config`, `--rule spring-config-safe` et `--rule spring-platform-no-modules` sortent en 0
- [x] `./gradlew build -x test`, `./gradlew test` et `./gradlew spotlessCheck` sortent en 0

### Étape 4 : module `orders`, domaine et cas d'usage (US-001 à US-005, T1 à T3, T5, T6, T8 côté fake)

Fichiers :
- `src/main/java/com/shop/orders/orders/package-info.java` (`@ApplicationModule(allowedDependencies = {"sharedkernel", "platform"})`, `@NullMarked`) et `package-info.java` de chaque sous-package
- `orders/domain/` : `OrderId`, `CustomerId`, `ProductId` (records sur `UUID`), `Order` (constructeur privé, `place(...)` → `Result<Order>`, `restore(...)`, `pullEvents()` vide), `OrderLine`, erreurs (`record` à code stable : `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `MIXED_CURRENCIES`, `CATALOG_UNAVAILABLE`, `ORDER_NOT_FOUND`, `VALIDATION_ERROR`), ports `ProductCatalog` (`Result<CatalogProduct> find(ProductId)`) et `OrderRepository` (`save`, `findByIdAndCustomer`, `findPageByCustomer(customer, after, limit)`)
- `orders/application/` : `PlaceOrder`, `GetOrder`, `ListOrders` (classes `final`, Java pur, `Clock` et `IdGenerator` injectés), commandes et vues `record` (`PlaceOrderCommand`, `OrderPage`, `OrderSummary` avec `itemCount` = nombre de lignes, `OrderCursor`)
- Tests (`src/test/java/com/shop/orders/orders/`) : `domain/OrderTest.java`, `application/PlaceOrderTest.java`, `application/GetOrderTest.java`, `application/ListOrdersTest.java` ; `fakes/FakeProductCatalog.java`, `fakes/InMemoryOrderRepository.java` ; suites de contrat abstraites `ProductCatalogContract.java` et `OrderRepositoryContract.java` (scénarios : enregistrer puis relire 2 lignes, lecture par le propriétaire / un autre client / un inconnu, pages de 2 sur 3 commandes de C1 + 1 de C2, deux commandes au même `createdAt` sans doublon) ; sous-classes `FakeProductCatalogContractTest.java`, `InMemoryOrderRepositoryContractTest.java`

Critères :
- [x] T1 passe : `Order.place` avec 10.00 EUR × 2 et 2.50 EUR × 4 → total 30.00 EUR, totaux de ligne 20.00 et 10.00, `pullEvents()` vide
- [x] T2 passe : zéro ligne, quantité 0, quantité 101, produit en double, 51 lignes → chacun `Failure` de genre `VALIDATION`
- [x] T3 passe : lignes EUR et USD → `Failure` `MIXED_CURRENCIES` de genre `CONFLICT`
- [x] T5 passe (`PlaceOrderTest`, fakes) : nom, prix et devise copiés du catalogue ; `Clock` et `IdGenerator` fixes donnent `createdAt` et `id` attendus ; un changement de prix du fake après création ne change pas la commande relue par `GetOrder`, et `GetOrder` n'appelle pas le catalogue (US-005)
- [x] T6 passe : produit inconnu, stock insuffisant, catalogue indisponible → `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `CATALOG_UNAVAILABLE` ; dépôt vide dans les trois cas
- [x] `FakeProductCatalogContractTest` et `InMemoryOrderRepositoryContractTest` passent (T8 et scénarios T14/T15 contre les fakes)
- [x] `GetOrderTest` et `ListOrdersTest` passent : commande d'un autre client = même `Failure` `ORDER_NOT_FOUND` qu'un inconnu ; `itemCount` = nombre de lignes ; `nextCursor` absent sur la dernière page
- [x] `ModularityTests` et `ArchitectureTests` passent (T20, partie modules)
- [x] `harness gate arch --rule spring-domain-pure`, `--rule spring-domain-no-annotation`, `--rule spring-domain-no-clock-random`, `--rule spring-no-cross-module-internals` et `--rule spring-amount-not-floating` sortent en 0
- [x] `./gradlew build -x test`, `./gradlew test` et `./gradlew spotlessCheck` sortent en 0

### Étape 5 : persistance PostgreSQL, migration `V1`, démarrage refusé sans schéma ni variable (US-002, US-003, US-005, US-006, T13 à T15, T18)

Fichiers :
- `src/main/resources/db/migration/V1__orders.sql` (nouveau, voir « Schéma `V1` » ; uniquement des créations de tables, d'index et de contraintes) ; révision (décision 15) : exactement les 8 contraintes nommées de l'état final (avec `ck_orders_order_line_line_total` ; **sans** `ck_orders_order_line_line_total_amount`, à retirer du fichier), modifiées dans `V1` même (fichier unique, aucune `V2`)
- `orders/infrastructure/` : entités `OrderEntity` (`@Table(name = "orders_order")`, `@Version`, constructeur `protected`, `equals`/`hashCode` sur l'id) et `OrderLineEmbeddable`, `OrderJpaRepository` (interface package-private, `@EntityGraph`, requête de page par `@Query` à paramètres liés), `JpaOrderRepository` (`@Repository` package-private, implémente `OrderRepository`), `OrderMapper` écrit à la main, projection `record` de la liste ; révision (décision 15) : `OrderEntity` numérote `line_number` à partir de 1 (`@ListIndexBase(1)` avec `@OrderColumn`, annotation vérifiée dans la version de Hibernate installée)
- `src/main/resources/application.yml` : profil `migrate` (décisions 3 et 4 : `web-application-type: none`, `spring.flyway.enabled: true`, `ignore-migration-patterns`, `app.service-name` et `app.shop-api.base-url` fixés)
- Révision (décision 14) : `src/main/java/com/shop/orders/platform/config/RequiredVariables.java` (nouveau : la liste unique, une ligne par variable avec ses profils ; valeur immuable, aucun état statique modifiable) et `RequiredVariablesEnvironmentPostProcessor.java` (nouveau : lit `RequiredVariables`, tient compte du profil `migrate`, s'exécute après le chargement de la configuration, lève une exception dont le message nomme les variables manquantes, sans valeur ; aucun `System.out`/`System.err`) ; enregistrement dans `src/main/resources/META-INF/spring.factories` sous la clé d'`EnvironmentPostProcessor` **lue dans le jar Spring Boot 4.1.1 installé** (le paquet de l'interface a changé avec Boot 4 ; ne pas supposer)
- Révision (décision 14) : `src/test/resources/config/application.yml` et `src/integrationTest/resources/config/application.yml` : valeurs de test non secrètes des 5 variables, pour que les contextes Spring des deux suites démarrent sans variable d'environnement définie (avec `@ServiceConnection`, la connexion réelle reste celle du conteneur)
- `src/integrationTest/java/com/shop/orders/PostgresContainer.java` (conteneur `postgres:17.11-alpine` partagé par la suite, `@ServiceConnection`) et `src/integrationTest/resources/config/application.yml` (Flyway activé pour les tests de persistance, valeurs non secrètes)
- `src/integrationTest/java/com/shop/orders/orders/infrastructure/JpaOrderRepositoryContractIT.java` (sous-classe de `OrderRepositoryContract`, `@DataJpaTest` + Testcontainers, plus le cas `@Version` et le cas `lineNumbersStartAtOne`, tous deux propres à JPA ; la suite partagée `OrderRepositoryContract` n'est pas modifiée)
- Révision (décision 15) : `src/integrationTest/java/com/shop/orders/orders/infrastructure/OrdersSchemaConstraintsIT.java` (nouveau, Testcontainers, schéma par Flyway) : écrit directement en base, hors des règles du domaine, par `JdbcClient` (ou `JdbcTemplate`) avec paramètres liés, jamais de SQL concaténé ; exactement un cas par contrainte de l'état final (8), chacun ne violant que la sienne, notamment prix négatif avec total cohérent (`-1.00` × 2 = `-2.00`) → seule `ck_orders_order_line_unit_price_amount`, et total positif différent de prix × quantité (`10.00` × 2 avec total `21.00`) → seule `ck_orders_order_line_line_total` ; le cas « total de ligne négatif » est supprimé ; l'écriture valide de référence respecte l'égalité ; aucune vérification par `pg_constraint` ni exception à la règle « un cas ne viole que sa contrainte »
- `src/integrationTest/java/com/shop/orders/RequiredEnvironmentIT.java` (décisions 8 et 14, T18 ; cas du profil `migrate` ajoutés) ; `src/integrationTest/java/com/shop/orders/MigrateProfileIT.java`
- Révision (décision 14) : `src/test/java/com/shop/orders/platform/config/RequiredVariablesEnvironmentPostProcessorTest.java` (nouveau, sans base ni contexte : `MockEnvironment` et appel direct du post-processeur ; liste attendue écrite en clair dans le test)
- `src/test/java/com/shop/orders/{HealthEndpointTest,DocsUiDisabledTest,OpenApiExportTest}.java` : port `OrderRepository` remplacé (`@MockitoBean`) pour démarrer sans base
- Non modifié : `src/test/java/com/shop/orders/RequiredEnvironmentTest.java` (étape 3, décision 14) ; `src/test/java/com/shop/orders/orders/contract/OrderRepositoryContract.java` (révision de la décision 15)

Critères :
- [x] T13 passe : une commande de 2 lignes enregistrée puis relue est égale à l'originale, montants à 2 décimales (`20.00`, pas `20`), version initialisée à 0 (décision 10)
- [x] T14 et T15 passent : `JpaOrderRepositoryContractIT` exécute la même suite `OrderRepositoryContract` que `InMemoryOrderRepositoryContractTest`, sans scénario propre hors `@Version`
- [x] T18 passe (`RequiredEnvironmentIT`) : sur une base au schéma vide, l'application démarrée sans Flyway échoue (`ddl-auto=validate`) et la base ne contient ensuite aucune table `orders_%`
- [x] `RequiredEnvironmentIT` passe aussi : avec les 5 variables pointant sur le conteneur et le schéma migré, le contexte démarre ; chacune retirée tour à tour, le démarrage échoue et la trace la nomme
- [x] `MigrateProfileIT` passe : l'application lancée avec le profil `migrate` et les seules variables `SPRING_DATASOURCE_*` sur une base vide crée `orders_order` et `orders_order_line`, sans serveur web (décisions 3 et 4)
- [x] `HealthEndpointTest`, `DocsUiDisabledTest` et `OpenApiExportTest` passent sans base, dans la suite `test`
- [x] `harness gate arch --rule spring-entity-in-adapters`, `--rule spring-jpa-lazy`, `--rule spring-no-query-concat`, `--rule spring-data-repo-package-private`, `--rule spring-sql-money-not-float`, `--rule spring-web-no-entity` et `--rule spring-tests-stack` sortent en 0
- [x] `RequiredVariablesEnvironmentPostProcessorTest` passe (sans base) : sans profil, chacune des 5 variables absente ou vide → exception dont le message la nomme ; avec le profil `migrate`, chacune des 3 `SPRING_DATASOURCE_*` absente → exception qui la nomme, et `APP_SERVICE_NAME` et `SHOP_API_BASE_URL` absentes → aucune exception ; toutes présentes → aucune exception ; avec des valeurs repérables (par exemple `valeur-temoin-123`), le message ne contient aucune valeur fournie
- [x] `RequiredEnvironmentIT` couvre le profil `migrate` : avec ce profil et les seules `SPRING_DATASOURCE_*`, le contexte démarre ; chacune des 3 retirée tour à tour, le démarrage échoue et la trace la nomme. Ces échecs portent le message du post-processeur (texte fixe vérifié par le test) : ils échoueraient si le post-processeur n'était pas enregistré
- [x] Liste unique : dans `src/main/java`, les littéraux `"APP_SERVICE_NAME"`, `"SHOP_API_BASE_URL"`, `"SPRING_DATASOURCE_URL"`, `"SPRING_DATASOURCE_USERNAME"` et `"SPRING_DATASOURCE_PASSWORD"` (nom seul entre guillemets) n'apparaissent que dans `RequiredVariables.java`, une entrée par variable ; le post-processeur ne nomme aucune variable lui-même (`grep -rn` de ces 5 littéraux sur `src/main/java`, tests exclus par construction)
- [x] `HealthEndpointTest`, `DocsUiDisabledTest`, `OpenApiExportTest` et `./gradlew -q openapiExport` passent avec le post-processeur actif, sans aucune des 5 variables définie dans l'environnement du processus ; `RequiredEnvironmentTest`, `MigrateProfileIT`, `JpaOrderRepositoryContractIT` et T18 passent toujours
- [x] `harness gate arch --rule spring-value-only-in-config`, `--rule spring-no-static-state`, `--rule spring-no-standard-streams`, `--rule spring-config-safe` et `--rule spring-platform-no-modules` sortent en 0
- [x] `./gradlew build -x test`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0
- [x] Migration (décision 15) : `V1__orders.sql` déclare les 8 contraintes sous leurs noms exacts (`ck_orders_order_item_count`, `ck_orders_order_total_amount`, `ck_orders_order_currency`, `ck_orders_order_line_line_number`, `ck_orders_order_line_quantity`, `ck_orders_order_line_unit_price_amount`, `ck_orders_order_line_line_total_amount`, `uq_orders_order_line_order_product`) avec les conditions de la décision 15 ; aucun fichier `V2__*.sql` n'existe
- [x] `OrdersSchemaConstraintsIT` passe : pour chacune des 8 contraintes, une écriture directe qui ne viole qu'elle (`item_count` 0 et 51, `total_amount` négatif, `currency` `eur`, `line_number` 0, `quantity` 0 et 101, `unit_price_amount` et `line_total_amount` négatifs, deux lignes du même produit dans une commande) est refusée par PostgreSQL, et l'erreur contient le nom de cette contrainte ; une écriture valide de référence passe. Le test échouerait si une contrainte manquait ou était renommée
- [x] `line_number` commence à 1 : après enregistrement par `JpaOrderRepository` d'une commande de 2 lignes, la lecture directe de `orders_order_line` (paramètres liés) donne `line_number` 1 et 2, et la relecture par le dépôt rend les lignes dans l'ordre d'origine ; `JpaOrderRepositoryContractIT` (suite `OrderRepositoryContract`, cas `@Version` compris) passe sans modification de ses scénarios
- [x] Non-régression après la modification de `V1` : T13, T14, T15 (`JpaOrderRepositoryContractIT`), T18 et les deux cas de `RequiredEnvironmentIT`, `MigrateProfileIT`, ainsi que `InMemoryOrderRepositoryContractTest`, sont relancés et passent sur le schéma contraint (leurs cases cochées ci-dessus sont reprouvées par celle-ci)
- [x] `harness gate arch --rule spring-no-query-concat`, `--rule spring-sql-money-not-float`, `--rule spring-jpa-lazy`, `--rule spring-entity-in-adapters` et `--rule spring-tests-stack` sortent en 0 ; `./gradlew build -x test`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0
- [x] Migration, 9e contrainte (décision 15, seconde révision) : `V1__orders.sql` déclare aussi `CONSTRAINT ck_orders_order_line_line_total CHECK (line_total_amount = unit_price_amount * quantity)` sur `orders_order_line` ; aucun fichier `V2__*.sql` n'existe
- [x] `lineNumbersStartAtOne` est un cas de `JpaOrderRepositoryContractIT`, propre à JPA : `src/test/java/com/shop/orders/orders/contract/OrderRepositoryContract.java` ne le contient pas et `InMemoryOrderRepositoryContractTest` exécute toujours exactement les scénarios de la suite partagée
- [x] Non-régression après l'ajout de la 9e contrainte : `./gradlew integrationTest` (dont T13 à T15, T18, `RequiredEnvironmentIT`, `MigrateProfileIT`, `JpaOrderRepositoryContractIT` et `lineNumbersStartAtOne`), `./gradlew test` et `./gradlew spotlessCheck` sortent en 0 ; `harness gate arch --rule spring-no-query-concat` et `--rule spring-sql-money-not-float` sortent en 0
- [x] État final de la migration (décision 15, troisième révision) : `V1__orders.sql` déclare exactement ces 8 contraintes nommées, chacune une fois, avec les conditions de la décision 15 : `ck_orders_order_item_count`, `ck_orders_order_total_amount`, `ck_orders_order_currency`, `ck_orders_order_line_line_number`, `ck_orders_order_line_quantity`, `ck_orders_order_line_unit_price_amount`, `ck_orders_order_line_line_total`, `uq_orders_order_line_order_product` ; il ne contient plus `ck_orders_order_line_line_total_amount` ; `V1__orders.sql` reste la seule migration de `db/migration` (aucun `V2__*.sql`)
- [x] `OrdersSchemaConstraintsIT` couvre l'état final : exactement un cas par contrainte des 8, chacun ne violant que la sienne, et l'erreur PostgreSQL contient le nom de cette contrainte ; dont prix négatif avec total cohérent (`-1.00` × 2 = `-2.00`) → `ck_orders_order_line_unit_price_amount` seule, et total positif incohérent (`10.00` × 2 avec `21.00`) → `ck_orders_order_line_line_total` seule ; plus aucun cas « total de ligne négatif » ; l'écriture valide de référence (par exemple `10.00` × 2 = `20.00`) passe. Le test échouerait si l'une des 8 contraintes manquait ou était renommée
- [x] Non-régression sur l'état final : `./gradlew integrationTest` (dont `OrdersSchemaConstraintsIT`, T13 à T15, T18, `RequiredEnvironmentIT`, `MigrateProfileIT`, `JpaOrderRepositoryContractIT` et `lineNumbersStartAtOne`), `./gradlew test` et `./gradlew spotlessCheck` sortent en 0 ; `harness gate arch --rule spring-no-query-concat` et `--rule spring-sql-money-not-float` sortent en 0

**Point d'arrêt (humain)**, après l'étape 5 : relire `src/main/resources/db/migration/V1__orders.sql` (types, les 8 contraintes nommées de la décision 15, dont `ck_orders_order_line_line_total` et sans `ck_orders_order_line_line_total_amount`, index `(customer_id, created_at DESC, id DESC)`). C'est la dernière occasion de modifier `V1` : une fois publiée dans une image `-migrate`, elle ne se modifie plus (correction = `V2`).

### Étape 6 : adaptateur shop-api d'après le contrat (US-004, T8, T9)

Fichiers :
- `orders/infrastructure/ShopApiProductCatalog.java` (nouveau, `@Component` package-private) : `GET /v1/products/{id}` ; `200` → produit (`id`, `name`, `price.amount` chaîne, `price.currency`, `stock`) lu dans un `record` qui ignore les champs inconnus ; `404` → `PRODUCT_NOT_FOUND` ; tout autre statut, corps illisible, devise ou montant refusé par `Money`, délai dépassé, connexion refusée → `CATALOG_UNAVAILABLE` ; journalisation SLF4J du statut ou du type d'erreur seulement (ni corps, ni identifiant de client)
- `orders/wiring/OrdersWiring.java` (nouveau, `@Configuration(proxyBeanMethods = false)` package-private) : bean `RestClient` de shop-api (URL et délais de `ShopApiProperties`)
- `src/test/resources/shop-api/` (fixtures) : `product-200.json`, `product-200-extra-field.json`, `product-404.json`, `product-400.json`, `product-500.json`, `product-unreadable.json`
- `src/integrationTest/java/com/shop/orders/orders/infrastructure/ShopApiProductCatalogContractIT.java` (sous-classe de `ProductCatalogContract`, `MockRestServiceServer`) ; `ShopApiTimeoutIT.java` (décision 7) ; `ShopApiContractConformanceIT.java` (T9, lecture de `docs/api/backends/shop-api.openapi.json` avec Jackson)

Critères :
- [x] T8 passe : `ShopApiProductCatalogContractIT` exécute la même suite `ProductCatalogContract` que `FakeProductCatalogContractTest` : produit lu (nom, prix, devise, stock) ; `404` → inconnu ; `400`, `500`, corps illisible → indisponible ; champ inconnu dans la réponse `200` ignoré
- [x] `ShopApiTimeoutIT` passe (décision 7) : un serveur local qui répond après 4 s donne `CATALOG_UNAVAILABLE` en moins de 3,9 s ; un port fermé donne `CATALOG_UNAVAILABLE`. Ce test échouerait si le délai de lecture n'était pas appliqué
- [x] T9 passe : `/v1/products/{id}` avec `get` existe dans `docs/api/backends/shop-api.openapi.json` ; ses réponses déclarent `200`, `400`, `404`, `500` ; le schéma `200` déclare `id`, `name`, `price.amount`, `price.currency`, `stock` ; chaque fixture `200` porte ces champs. Le test échouerait si un de ces éléments était retiré du fichier
- [x] `git diff --stat -- docs/api/backends/` est vide (fichiers du contrat de shop-api jamais modifiés)
- [x] `ModularityTests` et `ArchitectureTests` passent
- [x] `harness gate arch --rule spring-no-cross-module-internals`, `--rule spring-value-only-in-config` et `--rule spring-no-off-stack-libs` sortent en 0
- [x] `./gradlew build -x test`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0

### Étape 7 : endpoints `/v1/orders`, contrat OpenAPI, bout en bout (US-001 à US-005, T10 à T12, T16, T17, T22)

Fichiers :
- `orders/web/OrdersController.java` (`POST /v1/orders`, `GET /v1/orders/{orderId}`, `GET /v1/orders` ; en-tête `X-Customer-Id` UUID ; `@Validated`, `limit` 1 à 100, défaut 20 ; `Results.unwrap` ; `Location` sur 201), `PlaceOrderRequest` (`items` `@Size(min = 1, max = 50)`, `quantity` de 1 à 100), `OrderResponse`, `OrderLineResponse`, `MoneyResponse` (`amount` chaîne `^\d{1,15}\.\d{2}$`, devise `EUR|USD|GBP`), `OrderListResponse`, `OrderSummaryResponse`, `CursorCodec` ; documentation springdoc complète (`@Operation`, `@Parameter`, `@ApiResponse` 201/200, 400, 404 pour la lecture, 409 et 503 pour la création, 500, schéma `ApiError` ; `@Schema(description = …)` sur chaque champ)
- `orders/wiring/OrdersWiring.java` : beans `PlaceOrder`, `GetOrder`, `ListOrders`
- `src/test/java/com/shop/orders/orders/web/OrdersControllerTest.java` (`@WebMvcTest` + `MockMvcTester`, cas d'usage réels sur fakes)
- `src/test/java/com/shop/orders/OpenApiExportTest.java` : vérification des 3 opérations `/v1/orders`
- `src/integrationTest/java/com/shop/orders/orders/OrdersE2EIT.java` (`@SpringBootTest(RANDOM_PORT)`, Testcontainers, `RestClient` de shop-api remplacé par un client lié à `MockRestServiceServer`)
- `docs/api/openapi.json` (régénéré par `harness gate openapi --write`, qui lance la commande `openapi`, jamais écrit à la main)

Critères :
- [x] T10 passe : `POST /v1/orders` valide → `201`, `Location: /v1/orders/{id}`, corps `OrderResponse` (lignes avec `productName`, `unitPrice`, `quantity`, `lineTotal` ; `total` `30.00` `EUR`)
- [x] T11 passe : sans ligne, 51 lignes, quantité 0, quantité 101, produit en double, `X-Customer-Id` absent, non UUID, champ inconnu → `400` `VALIDATION_ERROR` au format commun, et le dépôt fake reste vide
- [x] T12 passe : `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `MIXED_CURRENCIES`, `CATALOG_UNAVAILABLE` → `400`, `409`, `409`, `503`, au format commun, corps sans nom de classe ni trace de pile
- [x] T16 passe : `GET /v1/orders/{id}` valide → `200` ; inconnu et autre client → `404` `ORDER_NOT_FOUND` aux corps identiques hors `traceId` ; id non UUID → `400` ; `GET /v1/orders` avec `limit` 0, 101, curseur illisible → `400` `VALIDATION_ERROR`
- [x] T17 passe (`OrdersE2EIT`) : commande créée, prix annoncé par le faux shop-api changé, commande relue avec prix et total inchangés ; aucune requête n'atteint le faux shop-api pendant la lecture ; la liste paginée renvoie la commande
- [x] T22 passe : `OpenApiExportTest` constate `/v1/orders` (`post`, `get`) et `/v1/orders/{orderId}` (`get`), chacun avec `summary`, description de `X-Customer-Id` et de chaque paramètre, réponses de succès et d'erreur décrites (présence testée, pas le texte)
- [x] `harness gate openapi --write` a régénéré `docs/api/openapi.json`, puis `harness gate openapi` sort en 0
- [x] `harness gate arch --rule spring-controller-thin`, `--rule spring-web-no-entity`, `--rule spring-no-cross-module-internals` et `--rule spring-tests-stack` sortent en 0 ; `ModularityTests` et `ArchitectureTests` passent (T20)
- [x] `./gradlew build -x test`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0

### Étape 8 : image `-migrate`, seed, compose avec base, runbook (US-006, T21)

Fichiers :
- `src/main/resources/application.yml` : profil `seed` (décision 5) ; `src/main/resources/db/seed/R__demo_orders.sql` (idempotent ; produits cités éventuellement absents de shop-api, sans effet ; respecte les 8 contraintes de la décision 15 : `line_number` à partir de 1, `line_total_amount = unit_price_amount * quantity`, prix positifs, `total_amount` = somme des totaux de ligne)
- `Dockerfile` : étape `migrate` **avant** l'étape finale (la cible par défaut reste l'application), sur `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24`, jar copié `--chown=1000:1000`, `USER 1000:1000`, `CMD ["java", "-jar", "/app/app.jar", "--spring.profiles.active=migrate"]`, `LABEL io.harness.seed="java -jar /app/app.jar --spring.profiles.active=migrate,seed"` ; ni `ENV` ni `ARG` de secret
- `compose.yaml` : service `db` (`postgres:17.11-alpine`, `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?…}`, volume nommé, `healthcheck` `pg_isready`, `restart`, port `127.0.0.1:${DB_PORT:-5432}:5432`) ; service `migrate` (`build` cible `migrate`, `restart: "no"`, `depends_on: db: service_healthy`, variables `SPRING_DATASOURCE_*`) ; `app` : `depends_on` `db` sain et `migrate` `service_completed_successfully`, variables `SPRING_DATASOURCE_*` et `SHOP_API_BASE_URL: ${SHOP_API_BASE_URL:?SHOP_API_BASE_URL manquante}` (sans valeur par défaut), `extra_hosts: ["host.docker.internal:host-gateway"]` avec le commentaire qui le justifie (décision 11 : nécessaire sous Docker Engine Linux, sans effet sous Docker Desktop)
- `.env.example` : `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD=change-me-local`, `DB_PORT`, et `SHOP_API_BASE_URL=http://host.docker.internal:8081` précédé d'un commentaire (shop-api lancé par son compose de développement, port 8081, à adapter) ; entrées existantes conservées
- `docs/runbooks/local.md` : lancer avec la base (`docker compose up -d --wait`), joindre shop-api (`SHOP_API_BASE_URL`, `host.docker.internal` et la raison de `extra_hosts` sous Linux), appliquer le seed, régénérer le contrat de shop-api (`harness tool contract-pull shop-api`, puis T9), preuves `compose-check` et `image-check` ; le retour arrière se prouve dans shop-platform (`rollback-check` à la PR de version)
- `src/integrationTest/java/com/shop/orders/SeedIT.java` (nouveau)

Critères :
- [x] `SeedIT` passe : profils `migrate,seed` lancés deux fois sur une base vide → même nombre de commandes de démonstration (> 0) après chaque passage ; puis `migrate` seul sur cette base démarre sans erreur (décision 5)
- [x] Le seed respecte `ck_orders_order_line_line_total` (décision 15) : `SeedIT` s'exécute sur le schéma contraint de `V1` sans violation, et une lecture directe des lignes de démonstration constate `line_total_amount = unit_price_amount * quantity` pour chacune
- [x] T21 : `harness gate arch --prefix platform-` sort en 0 (compose et Dockerfile sans `:latest`, sans secret en clair, sans `privileged` ni socket)
- [x] Le `Dockerfile` a une étape nommée `migrate` qui n'est pas la dernière, porte `LABEL io.harness.seed` et un `USER` numérique ; la dernière étape (cible par défaut) lance l'application (`CMD ["java", "-jar", "/app/app.jar"]`, `HEALTHCHECK` sur `/actuator/health`)
- [x] Dans `compose.yaml`, `SHOP_API_BASE_URL` est référencée sous la forme `${SHOP_API_BASE_URL:?…}` (aucune valeur par défaut) ; `.env.example` contient `SHOP_API_BASE_URL=http://host.docker.internal:8081` précédée d'une ligne de commentaire
- [x] Chaque variable référencée dans `compose.yaml` figure dans `.env.example` ; `db` n'expose qu'un port lié à `127.0.0.1` et configurable par `DB_PORT`
- [x] `docs/runbooks/local.md` contient `harness tool contract-pull shop-api`, `harness tool compose-check` et `harness tool image-check --build --requires-env`
- [x] `harness gate arch --rule spring-config-safe` sort en 0
- [x] `./gradlew build -x test`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0

#### Révision, décision 16 : arrêt du processus de migration

Placée à l'étape 8 plutôt qu'à l'étape 5 : le défaut s'est révélé à la preuve de l'étape 8 (`compose-check`), l'étape 5 et son point d'arrêt sont clos, et l'exécution reprend ici, juste avant de relancer la preuve qui a échoué. Le comportement corrigé est celui de l'image `-migrate` et de la commande de seed, livrées par cette étape.

Fichiers :
- `src/main/java/com/shop/orders/platform/config/MigrateExitRunner.java` (nouveau) : `ApplicationRunner`, `@Component`, `@Profile("migrate")`, `@ConditionalOnProperty(name = "app.migrate.exit-on-completion", havingValue = "true", matchIfMissing = true)` ; injection par constructeur du `ApplicationContext` ; `System.exit(SpringApplication.exit(context))` ; une ligne de journal SLF4J avant la sortie, sans valeur de configuration ; aucun champ statique modifiable, aucun `System.out`/`System.err`, aucun `@Value`, aucune dépendance vers un module métier
- `src/integrationTest/resources/config/application.yml` : `app.migrate.exit-on-completion: false` (tests en processus dans la JVM du worker Gradle)
- `src/integrationTest/java/com/shop/orders/MigrateProfileIT.java` et `SeedIT.java` : modifiés seulement s'ils n'appliquent pas `classpath:/config/application.yml` (argument `--app.migrate.exit-on-completion=false` dans ce cas) ; leurs scénarios ne changent pas
- `src/test/java/com/shop/orders/platform/config/MigrateExitRunnerTest.java` (nouveau, `ApplicationContextRunner`, sans base : il n'exécute jamais les `ApplicationRunner`, donc ne sort pas)
- `src/integrationTest/java/com/shop/orders/MigrateProcessExitIT.java` (nouveau) : processus séparé, voir décision 16 ; `waitFor(60, SECONDS)`, `destroyForcibly()` dans un `finally` quel que soit le résultat ; puis lecture des tables par `JdbcClient`/`JdbcTemplate` à paramètres liés

Critères :
- [x] `MigrateProcessExitIT` passe : le processus séparé (profil `migrate`, configuration de production seule, `SPRING_DATASOURCE_*` du conteneur seules, sans `APP_SERVICE_NAME` ni `SHOP_API_BASE_URL`) se termine avec le code 0 en moins de 60 s ; ensuite `orders_order` et `orders_order_line` existent ; le processus est tué dans tous les cas en fin de test. Ce test échouerait (délai dépassé) si `MigrateExitRunner` était retiré, ou si la sortie était désactivée par défaut dans la configuration de production
- [x] `MigrateExitRunnerTest` passe : avec le profil `migrate`, un bean `MigrateExitRunner` existe ; sans ce profil, aucun ; avec `migrate` et `app.migrate.exit-on-completion=false`, aucun
- [x] La JVM du worker n'est jamais arrêtée par l'exécuteur : `MigrateProfileIT` et `SeedIT` passent avec leurs scénarios inchangés, et `./gradlew integrationTest` exécute tous les tests de la suite jusqu'au bout (aucun « Gradle Test Executor finished with non-zero exit value »)
- [x] Non-régression : `./gradlew build -x test`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0 ; `harness gate arch --rule spring-no-static-state`, `--rule spring-no-standard-streams`, `--rule spring-value-only-in-config`, `--rule spring-platform-no-modules` et `--rule spring-config-safe` sortent en 0

**Point d'arrêt (humain)**, après l'étape 8. Dans un terminal, à la racine :
1. Ajouter `"extra": { "migrate": { "target": "migrate" } }` dans `publish` de `harness.config.json` (règle 5), sans toucher à `backends`
2. `harness ci --force` (décision 12, règle 4) : régénère `.github/workflows/harness.yml` avec la construction et la publication de l'image `-migrate`
3. `harness tool compose-check` (T23)
4. `harness tool image-check --build --requires-env` (T24 : image du service et image `-migrate`)

Après la révision de la décision 16, les gestes 1 et 2 sont déjà faits : l'humain relance seulement 3 et 4 (l'image `-migrate` change, ses deux preuves sont à refaire). Les rapports sont enregistrés dans `.claude/state/proofs/` : rien à coller. Un ❌ renvoie à l'étape 8 avec la correction du rapport.

### Étape 9 : lecture des preuves humaines (T23, T24)

Fichiers : aucun écrit. Lecture seule de `.claude/state/proofs/`, `harness.config.json` et `.github/workflows/harness.yml`. Un critère faux : s'arrêter et le signaler, sans rien retoucher.

Critères :
- [x] `harness.config.json` déclare `publish.extra.migrate.target = "migrate"` et toujours `backends.shop-api`
- [x] `.github/workflows/harness.yml` publie `ghcr.io/rolandrm/shop-orders-migrate` sur les tags `v*`, à la même version que l'image de l'application
- [x] T23 : le rapport `compose-check` de `.claude/state/proofs/` finit par « ✅ Tous les contrôles passent » (`db` healthy, `migrate` terminé en 0 avant `app`, seed appliqué sous l'utilisateur de l'image, `app` healthy)
- [x] T24 : le rapport `image-check` finit par « ✅ Tous les contrôles passent » et couvre l'image du service et l'image `-migrate` (construites, sans secret, utilisateur non root)

## Couverture du plan de test

| Test | Étape |
|---|---|
| T1, T2, T3, T5, T6 | 4 |
| T4, T7 | 3 |
| T8 | 4 (fake), 6 (adaptateur) |
| T9 | 6 |
| T10, T11, T12, T16, T17, T22 | 7 |
| T13, T14, T15, T18 | 5 (T14/T15 aussi contre le fake à l'étape 4) |
| T19 | invariants répartis aux étapes 1, 3 à 7 ; tous relancés par `harness verify` |
| T20 | 4 à 7 |
| T21 | 8 |
| T23, T24 | point d'arrêt de l'étape 8 (relancés après la décision 16), lus à l'étape 9 |
| Contraintes de schéma (décision 15, 8 contraintes, hors plan de test de la spec) | 5 (`OrdersSchemaConstraintsIT`, `lineNumbersStartAtOne` dans `JpaOrderRepositoryContractIT`) ; seed : 8 (`SeedIT`) |
| Arrêt du processus de migration (décision 16, US-006, condition de T23) | 8, révision (`MigrateProcessExitIT`, `MigrateExitRunnerTest`) |

## Hors plan

- `harness verify` complet : lancé par l'orchestrateur en fin de génération
- Retour arrière (décision 13, spec révision 1) : `harness tool rollback-check` ne tourne que dans le repo plateforme ; il se prouve dans shop-platform à la PR de version 0.2.0 (N-1 = 0.1.0, sans base)
- Vérification après déploiement (spec) : après la fusion et le premier tag, dans le repo plateforme
- Authentification (`X-Customer-Id` non vérifié), idempotence, réservation de stock : exclus par la spec
