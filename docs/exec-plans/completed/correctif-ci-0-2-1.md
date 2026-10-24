# Plan : correctif-ci-0-2-1

- **Statut** : COMPLETED
- **Taille** : M
- **Durée attendue** : 105 min (médiane de 2 cycles M sur ce poste, `.claude/state/cycle-times.json` : 174 et 36 min)
- **Points d'arrêt** :
  - après l'étape 3 (geste humain, règle 5 : changer la commande `build` de `harness.config.json`, avant la montée de version et avant toute CI de la branche)
  **Note** : l'humain approuve ce plan en tapant `/harness:ship docs/specs/correctif-ci-0-2-1.md --continue` ; il passe `COMPLETED` sur preuve en fin de cycle (toutes les cases cochées, `harness verify` vert).

- **Spec** : `docs/specs/correctif-ci-0-2-1.md` (Approved, v0.1.0, aucune révision)
- **Aucune dépendance ajoutée** : pas d'étape « Dépendances » (JUnit 5, AssertJ, `MockRestServiceServer`, Testcontainers, Bean Validation déjà déclarés).
- **Jamais modifiés par une étape** : `harness.config.json` (geste humain du point d'arrêt), `.github/workflows/`, `docs/decisions/`, `docs/architecture-debt.json`, `docs/architecture-invariants.json`, `docs/api/backends/`, `docs/exec-plans/completed/service-foundation.md` (et tout plan archivé).

## Questions à l'humain

aucune (les 2 questions ont reçu leur réponse, reportée ci-dessous et dans les étapes)

## Décisions (réponses de l'humain)

1. **Sondes T1 et T2 par méthode temporaire** : oui. Le generator ne pouvant ni supprimer ni déplacer un fichier, la sonde n'est pas un fichier : c'est une méthode de test temporaire ajoutée par Edit dans un test existant, `ResultTest` (suite `test`) et `ShopApiContractConformanceIT` (suite `integrationTest`, sans Docker ni Spring), puis retirée par Edit dans la même étape (étape 1). **Marque unique de sonde : `PROBE-CI-0-2-1`**, présente dans le message de l'exception externe et dans celui de sa cause, pour chacune des deux sondes (un nom de méthode Java ne peut pas contenir de tiret ; les méthodes s'appellent `sondeT1` et `sondeT2`). Après le retrait, `grep -rn "PROBE-CI-0-2-1" src/` sort en 1 (aucune occurrence).
2. **Nom de 200 caractères stocké en base** : oui. Cas propre à JPA dans `JpaOrderRepositoryContractIT` (comme `lineNumbersStartAtOne`) : une commande dont la ligne porte un nom de 200 caractères est enregistrée puis relue à l'identique ; la suite partagée `OrderRepositoryContract` reste intacte (étape 3).

## Décisions de conception (sans question)

- **Affichage des échecs** : configuré dans le bloc existant `tasks.withType<Test>().configureEach` de `build.gradle.kts` (couvre `test`, `integrationTest` et aussi `openapiExport`) : `testLogging` avec l'événement `FAILED`, `exceptionFormat = FULL`, `showExceptions`, `showCauses`, `showStackTraces` à vrai, `showStandardStreams` à faux (les journaux SLF4J des tests restent dans les rapports, pas dans la sortie). Le récapitulatif (passés, échoués, ignorés) est écrit à la fin de la suite racine (descripteur sans parent) **au niveau `lifecycle` du logger Gradle, jamais par `println`** : `./gradlew -q openapiExport` doit continuer à n'afficher que le JSON du contrat (commande `openapi` lue par `harness gate openapi`).
- **Utilitaire d'attente** : `src/test/java/com/shop/orders/ProcessAwait.java` (paquet racine `com.shop.orders`, comme `MigrateProcessExitIT` ; suite `test`, donc visible de `integrationTest` qui voit `sourceSets["test"].output`). Classe `final` sans état, méthode statique du type `int awaitExit(Process process, Duration timeout, Path log)` : rend le code 0 ; sur délai dépassé, tue le processus (`destroyForcibly()` puis attente de sa fin) et lève une `AssertionError` dont le message contient le délai et les 100 dernières lignes de `log` ; sur code non nul, `AssertionError` avec le code et les 100 dernières lignes. Le message ne contient aucune variable d'environnement. Constante du nombre de lignes : `static final int` (aucun état statique modifiable). Aucun `System.out`/`System.err`.
- **Processus de test de `ProcessAwaitTest`** : `/bin/sh -c` (POSIX : CI `ubuntu-latest` et macOS), sortie redirigée vers un fichier d'un `@TempDir` JUnit (nettoyé par JUnit, aucun fichier laissé). Le script qui dort se termine par `exec sleep 60`, pour que le processus tué soit bien celui qui dort (pas un `sleep` orphelin qui garderait le fichier ouvert).
- **Délai de `MigrateProcessExitIT`** : constante nommée `static final Duration MIGRATE_TIMEOUT = Duration.ofSeconds(180)` (package-private, lue par l'assertion de T7) ; `try/finally` avec `destroyForcibly()` conservé ; journal toujours `build/migrate-process-exit.log`.
- **Borne du nom** : `@Size(max = 200)` sur `name` de `ShopApiProductCatalog.ProductResponse`, en plus de `@NotBlank`. T9 lit la borne de l'adaptateur par réflexion sur le **champ** `name` du record (`ProductResponse.class.getDeclaredField("name").getAnnotation(Size.class).max()`, depuis `ShopApiContractConformanceIT`, même paquet) : `@Size` n'a pas la cible `RECORD_COMPONENT`, l'annotation se propage au champ. Le contrat déclare `maxLength: 200` pour `name` dans les deux schémas `200` (liste et lecture) ; T9 lit celui de `GET /v1/products/{id}`.
- **Relancer une suite à jour** : les preuves de sortie de Gradle (T1 à T3) utilisent `--rerun` (sinon une tâche `UP-TO-DATE` n'affiche rien).

## Étapes

### Étape 1 : cause des tests en échec affichée par Gradle (US-001, T1, T2, T3)

Fichiers :
- `build.gradle.kts` : bloc `tasks.withType<Test>().configureEach` complété (voir « Affichage des échecs ») ; la propriété système `project.version` existante est conservée
- Sondes (décision 1, marque `PROBE-CI-0-2-1`) : `src/test/java/com/shop/orders/sharedkernel/ResultTest.java` et `src/integrationTest/java/com/shop/orders/orders/infrastructure/ShopApiContractConformanceIT.java` reçoivent par Edit une méthode temporaire (`sondeT1`, `sondeT2`) qui lève `new IllegalStateException("PROBE-CI-0-2-1 externe", new IllegalArgumentException("PROBE-CI-0-2-1 cause"))`, puis la perdent par Edit dans cette même étape (aucun fichier créé pour les sondes)

Critères :
- [x] T1 : avec la sonde `sondeT1` (marque `PROBE-CI-0-2-1`) dans `ResultTest`, `./gradlew test --rerun --tests 'com.shop.orders.sharedkernel.ResultTest.sondeT1'` échoue, et sa sortie contient, avant `BUILD FAILED` et avant le récapitulatif de la tâche : le nom `sondeT1`, `PROBE-CI-0-2-1 externe`, `Caused by:` suivi de `PROBE-CI-0-2-1 cause`, et au moins une ligne de pile `at com.shop.orders.sharedkernel.ResultTest`
- [x] T2 : avec la sonde `sondeT2` (marque `PROBE-CI-0-2-1`) dans `ShopApiContractConformanceIT`, `./gradlew integrationTest --rerun --tests 'com.shop.orders.orders.infrastructure.ShopApiContractConformanceIT.sondeT2'` échoue avec le même contenu (`sondeT2`, `PROBE-CI-0-2-1 externe`, `Caused by:` suivi de `PROBE-CI-0-2-1 cause`, pile `at com.shop.orders.orders.infrastructure.ShopApiContractConformanceIT`)
- [x] Sondes retirées : après le retrait, `grep -rn "PROBE-CI-0-2-1" src/` sort en 1 (aucune occurrence), donc ni `ResultTest` ni `ShopApiContractConformanceIT` ne la contiennent plus ; aucun fichier sous `src/` ne contient `sondeT1` ni `sondeT2` ; `ResultTest` et `ShopApiContractConformanceIT` passent avec leurs seuls tests d'origine
- [x] T3 : `./gradlew test --rerun` sort en 0 ; sa sortie contient le récapitulatif (nombre de tests passés, échoués = 0, ignorés) et ne contient ni `Caused by:` ni ligne de pile `at com.shop.orders`
- [x] `./gradlew -q openapiExport` n'affiche que le contrat : `harness gate openapi` sort en 0 (le récapitulatif n'est pas écrit en mode `-q`)
- [x] `./gradlew build -x test -x integrationTest`, `./gradlew test` et `./gradlew spotlessCheck` sortent en 0

### Étape 2 : attente du processus `migrate` avec diagnostic (US-002, T4 à T7)

Fichiers :
- `src/test/java/com/shop/orders/ProcessAwait.java` (nouveau, voir « Utilitaire d'attente »)
- `src/test/java/com/shop/orders/ProcessAwaitTest.java` (nouveau, JUnit 5 + AssertJ, sans Spring)
- `src/integrationTest/java/com/shop/orders/MigrateProcessExitIT.java` : `waitFor(60, SECONDS)` et les deux assertions remplacés par `ProcessAwait.awaitExit(process, MIGRATE_TIMEOUT, log)` ; constante `MIGRATE_TIMEOUT` ; environnement du processus inchangé (les trois `SPRING_DATASOURCE_*` seules) ; vérification des tables inchangée

Critères :
- [x] T4 passe (`ProcessAwaitTest`) : un processus qui écrit `ligne-1` à `ligne-150` puis dort, délai de 2 s → `AssertionError` dont le message contient chacune des lignes `ligne-51` à `ligne-150` et aucune ligne égale à `ligne-1` (comparaison ligne par ligne, `ligne-1` étant préfixe de `ligne-10`…) ; après l'appel, `process.isAlive()` est faux
- [x] T5 passe : un processus qui écrit une ligne puis sort avec le code 3 → `AssertionError` dont le message contient `3` comme code de sortie et la ligne écrite
- [x] T6 passe : un processus qui sort avec le code 0 avant le délai → aucune exception, `0` rendu
- [x] T7 passe (`MigrateProcessExitIT`) : une assertion vérifie `MIGRATE_TIMEOUT` = 180 s ; le processus `migrate` sort en 0 et `orders_order` et `orders_order_line` existent
- [x] Le message d'échec ne contient aucune variable d'environnement : `ProcessAwait.java` n'appelle ni `System.getenv` ni `ProcessBuilder.environment()`
- [x] T13 (partie US-002) : `harness gate arch --rule spring-no-standard-streams`, `--rule spring-tests-stack` et `--rule spring-no-static-state` sortent en 0
- [x] `./gradlew build -x test -x integrationTest`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0 (`MigrateProfileIT`, `SeedIT`, `RequiredEnvironmentIT` passent sans modification)

### Étape 3 : nom de produit borné à 200 caractères (US-003, T8, T9, T10)

Fichiers :
- `src/main/java/com/shop/orders/orders/infrastructure/ShopApiProductCatalog.java` : `@Size(max = 200)` sur `ProductResponse.name` (import `jakarta.validation.constraints.Size`) ; Javadoc de la classe complétée (nom de 1 à 200 caractères, sinon « indisponible ») ; comportement inchangé pour le reste
- `src/integrationTest/java/com/shop/orders/orders/infrastructure/ShopApiProductCatalogContractIT.java` : cas ajoutés (corps `200` en ligne, comme `bodyOutsideTheContractIsReportedAsUnavailable`, nom ASCII)
- `src/integrationTest/java/com/shop/orders/orders/infrastructure/ShopApiContractConformanceIT.java` : cas T9 ajouté
- Décision 2 : `src/integrationTest/java/com/shop/orders/orders/infrastructure/JpaOrderRepositoryContractIT.java` : cas propre à JPA (nom de 200 caractères enregistré puis relu) ; `src/test/java/com/shop/orders/orders/contract/OrderRepositoryContract.java` non modifié

Critères :
- [x] T8 passe (`ShopApiProductCatalogContractIT`) : nom de 200 caractères → `Success` avec ce nom entier ; 1 caractère → `Success` avec ce nom ; 201 caractères, `""` et `"   "` → `Failure` `CATALOG_UNAVAILABLE` ; le cas 201 échouerait sans `@Size(max = 200)`
- [x] Les tests existants de `ShopApiProductCatalogContractIT` (suite `ProductCatalogContract` comprise) et `FakeProductCatalogContractTest` passent sans modification de leurs scénarios
- [x] T9 passe (`ShopApiContractConformanceIT`) : `maxLength` de `name` dans le schéma `200` de `GET /v1/products/{id}` de `docs/api/backends/shop-api.openapi.json` vaut 200, et `max` de `@Size` lu sur le champ `name` de `ProductResponse` lui est égal ; le test échouerait si l'annotation manquait ou si les deux valeurs divergeaient
- [x] T10 : `OrdersControllerTest.t12CatalogUnavailableGives503` passe sans modification (503 `CATALOG_UNAVAILABLE` au format commun, dépôt vide)
- [x] Décision 2 : le cas JPA passe : une commande dont la ligne a un nom de 200 caractères est enregistrée par `JpaOrderRepository` puis relue avec ce nom entier ; `OrderRepositoryContract` ne contient pas ce cas et `InMemoryOrderRepositoryContractTest` exécute toujours exactement les scénarios de la suite partagée
- [x] `git diff --stat -- docs/api/backends/` est vide
- [x] T13 (partie US-003) : `harness gate arch --rule spring-no-cross-module-internals` et `--rule spring-value-only-in-config` sortent en 0 ; `ModularityTests` et `ArchitectureTests` passent
- [x] `./gradlew build -x test -x integrationTest`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0

**Point d'arrêt (humain)**, après l'étape 3. Dans un éditeur (règle 5) : dans `harness.config.json`, stack `api`, remplacer `"build": "./gradlew build -x test"` par `"build": "./gradlew build -x test -x integrationTest"` ; ne rien changer d'autre (`test`, `test:integration`, `lint`, `openapi`, `publish`, `backends` inchangés). À faire avant toute CI de la branche. Puis reprendre le cycle.

### Étape 4 : commande `build` constatée et version 0.2.1 (US-004, US-005, T11, T12)

Dernière étape de code. Lecture seule de `harness.config.json` : un critère T11 faux arrête le cycle et se signale, sans rien retoucher.

Fichiers :
- `build.gradle.kts` : `version = "0.2.1"`
- `docs/api/openapi.json` : régénéré par `harness gate openapi --write` (jamais écrit à la main)

Critères :
- [x] T11 : `harness.config.json` déclare, pour la stack `api`, `build` = `./gradlew build -x test -x integrationTest`, et `test` = `./gradlew test`, `test:integration` = `./gradlew integrationTest` inchangés
- [x] T11 : la liste des tâches de `./gradlew build -x test -x integrationTest --dry-run` ne contient ni `:test` ni `:integrationTest`, et contient `:compileJava` et `:spotlessCheck`
- [x] T12 : après `harness gate openapi --write`, `harness gate openapi` sort en 0 ; `info.version` de `docs/api/openapi.json` vaut `0.2.1`
- [x] Le contrat ne change que par sa version : `git diff -- docs/api/openapi.json` ne montre que la ligne `"version"` de `info` (`0.2.0` → `0.2.1`)
- [x] `OpenApiExportTest` passe : `info.version` égale `project.version` (`0.2.1`)
- [x] `./gradlew build -x test -x integrationTest`, `./gradlew test`, `./gradlew integrationTest` et `./gradlew spotlessCheck` sortent en 0

## Couverture du plan de test

| Test | Étape |
|---|---|
| T1, T2, T3 | 1 (sondes par méthode temporaire marquée `PROBE-CI-0-2-1`, retirées dans l'étape, décision 1) |
| T4, T5, T6, T7 | 2 |
| T8, T9, T10 | 3 |
| US-003, nom de 200 caractères stocké en base (décision 2, hors plan de test de la spec) | 3 (cas propre à JPA dans `JpaOrderRepositoryContractIT`) |
| T11, T12 | 4 (après le geste humain du point d'arrêt) |
| T13 | 2 (`spring-no-standard-streams`, `spring-tests-stack`, `spring-no-static-state`) et 3 (`spring-no-cross-module-internals`, `spring-value-only-in-config`) ; toutes relancées par `harness verify` |
| T15 | `harness verify` final de l'orchestrateur, sur la commande `build` changée : vert, `api:test` et `api:test:integration` listés chacun une fois |
| T14 | hors plan (geste humain après fusion et tag) |

## Hors plan

- `harness verify` complet : lancé par l'orchestrateur en fin de génération (preuve de T15)
- Après la fusion sur main (CI verte) : l'humain pose le tag `v0.2.1` sur main ; la CI publie `ghcr.io/rolandrm/shop-orders:0.2.1` et `ghcr.io/rolandrm/shop-orders-migrate:0.2.1` ; puis l'humain lance `harness tool release-check v0.2.1` (T14), rapport enregistré dans `.claude/state/proofs/`, attendu « ✅ Tous les contrôles passent »
- Retour arrière : se prouve dans shop-platform, à la PR de version qui adopte la `0.2.1` (N-1 = `0.2.0`, avec base)
- Vérification après déploiement (`/actuator/health`, `info.version` `0.2.1`, `POST /v1/orders` → `201`) : dans le repo plateforme, après déploiement
- Lecture du journal de la CI au prochain échec, cause racine du thread non démon de `migrate`, contrôle de la devise dans l'adaptateur : exclus par la spec (une autre spec si besoin)
