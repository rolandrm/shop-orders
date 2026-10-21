# Plan : service-foundation

- **Statut** : COMPLETED
- **Taille** : M
- **Durée attendue** : à mesurer (aucun cycle de cette taille sur ce poste : `.claude/state/cycle-times.json` absent)
- **Points d'arrêt** :
  - après l'étape 1 (dépendances à installer : wrapper Gradle, téléchargement des dépendances et de la toolchain Java 21, écriture de `gradle.lockfile`, tout cela demande le réseau, refusé aux agents)
  - après l'étape 5 (`harness init` modifie `harness.config.json` : geste humain, règle 5)
  **Note** : l'humain approuve ce plan en tapant `/harness:ship docs/specs/service-foundation.md --continue` ; il passe `COMPLETED` sur preuve en fin de cycle (toutes les cases cochées, `harness verify` vert).

Spec : `docs/specs/service-foundation.md` (Approved). État actuel du dépôt : aucun build, `harness.config.json` sans stack, seuls existent
`src/test/java/com/shop/orders/ModularityTests.java` et `ArchitectureTests.java` (package `com.shop.orders` déjà en place ; ils référencent
`com.shop.orders.Application`, `org.springframework.modulith.docs.Documenter`, `jakarta.persistence.Entity`,
`org.springframework.data.repository.Repository`, `org.springframework.transaction.annotation.Transactional`).
Fragments Harness à fusionner : `~/.claude/plugins/cache/harness-tools/harness/0.7.18/templates/springboot/build-conventions.gradle.kts`
et `.../application-architecture.yml`.

## Questions à l'humain

1. **Versions à figer** (le planner n'a pas de réseau et ne peut pas lire les dernières versions publiées). La spec demande « la dernière
   version stable de Spring Boot et les versions annoncées compatibles ». Figer : Spring Boot 4.1.x (dernier patch), Spring Modulith de la
   même génération (2.1.x), springdoc-openapi 3.x compatible Boot 4, ArchUnit 1.4.x, Error Prone 2.x, NullAway 0.12.x, plugin
   `net.ltgt.errorprone` 4.x, Spotless 8.x, Gradle 9.x, foojay-resolver 1.x ? Donnez les numéros de patch exacts dans la réponse ; ils seront
   reportés tels quels dans `gradle/libs.versions.toml` à l'étape 1. **Proposé : oui, avec les numéros que vous fournissez.**
   **Réponse (humain) : Spring Boot 4.1.1, Spring Modulith 2.1.1, springdoc-openapi 3.1.1, ArchUnit 1.5.1, Error Prone 2.50.0, NullAway
   0.14.2, plugin `net.ltgt.errorprone` 5.1.1, Spotless 8.10.4, plugin `io.spring.dependency-management` 1.1.7, foojay-resolver-convention
   1.0.0, Gradle 9.8.2, JSpecify 1.0.1.**
2. Avec Spring Boot 4, MockMvc et `@WebMvcTest` sont dans un module à part. Ajouter `spring-boot-starter-webmvc-test` en `testImplementation`
   (en plus de `spring-boot-starter-test`) et utiliser `spring-boot-starter-webmvc` à la place de `spring-boot-starter-web`, l'ancien nom
   étant déprécié ? **Proposé : oui** (si vous restez en Boot 3.x : `spring-boot-starter-web` et `spring-boot-starter-test` suffisent).
3. L'invariant `spring-gradle-versions-in-catalog` interdit `version "x"` dans `settings.gradle.kts`, et le catalogue n'est pas lisible dans le
   bloc `plugins` de `settings`. Mettre la version du plugin foojay-resolver dans `gradle.properties` (`foojayResolverVersion=…`) et la lire
   dans `pluginManagement` ? **Proposé : oui.**
4. La spec exclut les logs JSON, mais le fragment `application-architecture.yml` impose `logging.structured.format.console: ecs`. Retirer cette
   ligne du `application.yml` fusionné (elle reviendra avec la spec d'observabilité) ? **Proposé : oui, la retirer.**
5. Le fragment contient aussi des réglages `spring.jpa.*` et `spring.flyway.*`, alors que ce socle n'a ni JPA ni Flyway. Les garder pour que le
   fragment soit fusionné à l'identique (ils restent sans effet sans JPA) ? **Proposé : oui.**
6. Sharedkernel : la règle `sharedkernel.md` demande `@ApplicationModule(type = Type.OPEN)` dans son `package-info.java`, mais la règle ArchUnit
   `domain_is_pure` de `ArchitectureTests` (que l'agent ne modifie pas) peut compter cette annotation Spring comme une dépendance du package.
   Si T12 échoue sur ce seul point, garder seulement `@NullMarked` dans le `package-info` du sharedkernel ? Son API est entièrement dans son
   package racine, donc OPEN ne change rien à `verify()`. **Proposé : oui.** `platform` garde `@ApplicationModule(type = OPEN)` dans tous les cas.
7. Avec les probes activées, `GET /actuator/health` renvoie `{"status":"UP","groups":["liveness","readiness"]}` et non exactement
   `{"status":"UP"}`. T1 vérifie-t-il `status == "UP"` et l'absence de `components` plutôt que l'égalité stricte du corps ? **Proposé : oui.**
8. La spec ne fixe ni les codes d'erreur hors `DomainError` ni la valeur de `traceId` (pas d'OpenTelemetry dans ce socle). Codes proposés :
   `VALIDATION_ERROR` (400 de Bean Validation, `details` = liste `{ field, message }`), `INTERNAL_ERROR` (500, message générique fixe). Une
   exception Spring qui porte déjà un statut (`org.springframework.web.ErrorResponse` : route inconnue, méthode non permise…) garde ce statut,
   avec pour code le nom du statut (`NOT_FOUND`, `METHOD_NOT_ALLOWED`). `traceId` = valeur de `X-Request-Id` (lue dans le MDC). **Proposé : oui.**
9. Le nom du service (US-004) : propriété `app.service-name` (record `AppProperties`, préfixe `app`), valeur `shop-orders` dans
   `application.yml` ? **Proposé : oui.**
10. `docs/specs/squelette.md` (Draft, non suivi par git) recouvre presque entièrement cette spec. L'abandonner au profit de
    `service-foundation` (suppression par vous, hors de ce plan) ? **Proposé : oui.**

**Réponses (humain) : Q2 à Q9 : réponses proposées acceptées. Q10 : `docs/specs/squelette.md` supprimée.**

## Décisions de conception (sans question)

- Tous les tests de ce socle vivent dans `src/test` et tournent avec `./gradlew test` (aucun Testcontainers ici). La suite `integrationTest`
  du fragment est déclarée et rattachée à `check`, vide pour l'instant.
- T1 et T2 (`@SpringBootTest(RANDOM_PORT)`) appellent le port aléatoire avec `RestClient` (`spring-web`, déjà présent), sans module de test
  supplémentaire.
- Le contrôleur de test de T7 à T9 vit dans `src/test/java`, **hors** du package `com.shop.orders` (par ex. `src/test/java/errorfixture/`),
  et n'est chargé que par `@Import` dans les tests qui l'utilisent. Ainsi le scan des composants, `OpenApiExportTest` et `ModularityTests` ne le
  voient pas, et il n'apparaît pas dans le contrat.
- `platform.web.Results.unwrap(Result<T>)` renvoie la valeur d'un `Success`, ou lève `DomainErrorException` (dans `platform.web`) qui porte le
  `DomainError` ; l'advice la convertit. Correspondance : `VALIDATION` → 400, `NOT_FOUND` → 404, `CONFLICT` → 409, `UNEXPECTED` → 500.
- Toutes les dépendances sont déclarées à l'étape 1. Aucune étape suivante n'en ajoute : si une classe manque, c'est un écart, et on s'arrête.

## Étapes

### Étape 1 — Build Gradle et point d'entrée de l'application

Fichiers : `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `.java-version`, `.gitignore`,
`src/main/java/com/shop/orders/Application.java`.

- `build.gradle.kts` = fragment `build-conventions.gradle.kts` fusionné (`<base-package>` → `com.shop.orders`), toolchain via
  `libs.versions.java` (21), `dependencyLocking { lockAllConfigurations() }`, suite `integrationTest` rattachée à `check`.
- Dépendances (alias du catalogue, versions selon Q1/Q2) : `implementation` = starter web MVC, `spring-boot-starter-actuator`,
  `spring-boot-starter-validation`, `spring-modulith-starter-core`, `springdoc-openapi-starter-webmvc-ui`, `jspecify` ;
  `errorprone` = `error_prone_core`, `nullaway` ; `testImplementation` = `spring-boot-starter-test` (et `spring-boot-starter-webmvc-test`
  selon Q2), `spring-modulith-starter-test`, `spring-modulith-docs`, `archunit-junit5`, `jakarta.persistence-api`, `spring-data-commons`,
  `spring-tx` (s'il n'arrive pas en transitif : `ArchitectureTests` importe `@Transactional`). BOM : Spring Boot + `spring-modulith-bom`.
- Tâche `openapiExport` déclarée **à l'étape 5**, pas ici.
- `settings.gradle.kts` : `rootProject.name = "shop-orders"`, plugin foojay-resolver-convention (version selon Q3).
- `.java-version` : `21`. `.gitignore` : ajouter `build/`, `.gradle/`, `*.iml`, `.idea/` sans retirer les entrées existantes.
- `Application.java` : `@SpringBootApplication` + `@Modulithic`, `main` qui lance `SpringApplication.run`.

Critères :
- [x] `harness gate arch --rule spring-gradle-versions-in-catalog` sort en 0
- [x] `harness gate arch --rule spring-gradle-off-stack` sort en 0
- [x] `gradle/libs.versions.toml` contient toutes les versions de Q1 (Boot 4.1.1, Modulith 2.1.1, springdoc 3.1.1, ArchUnit 1.5.1, Error Prone 2.50.0, NullAway 0.14.2, plugin errorprone 5.1.1, Spotless 8.10.4, dependency-management 1.1.7, JSpecify 1.0.1 ; foojay 1.0.0 dans `gradle.properties`), sans `+`, `latest` ni plage
- [x] `.gitignore` contient `build/`, `.gradle/`, `*.iml`, `.idea/` et toutes ses entrées précédentes

**Point d'arrêt (dépendances à installer, humain).** À la racine du repo, avec la version Gradle de Q1 :

```sh
gradle wrapper --gradle-version 9.8.2
./gradlew --write-locks dependencies compileJava spotlessApply
```

La première commande crée `gradlew`, `gradlew.bat` et `gradle/wrapper/`. La seconde télécharge la toolchain 21, les dépendances, Error Prone
et google-java-format, puis écrit `gradle.lockfile`. Collez la fin de la sortie (`BUILD SUCCESSFUL`). En cas d'échec de résolution (version
incompatible), corrigez la version dans le catalogue ou renvoyez l'erreur au planner.

- [x] (humain) `./gradlew --write-locks dependencies compileJava spotlessApply` sort en 0 ; `gradlew`, `gradle/wrapper/gradle-wrapper.jar` et `gradle.lockfile` existent

### Étape 2 — sharedkernel : `Result` et `DomainError`, verrous d'architecture verts

Fichiers : `src/main/java/com/shop/orders/sharedkernel/{Result.java, DomainError.java, package-info.java}`,
`src/test/java/com/shop/orders/sharedkernel/ResultTest.java`, `src/main/java/com/shop/orders/platform/package-info.java` (déplacé de l'étape 3),
`src/test/resources/archunit.properties` (révision du plan). `ModularityTests.java` et `ArchitectureTests.java` sont seulement exécutés
(leurs règles ne changent pas) ; `ArchitectureTests.java` est reformaté par `./gradlew spotlessApply`, lancé par l'humain (commande hors liste du generator).

- Révision (étape 2 bloquée) : `Application.java` déclare `platform` module partagé, donc `platform/package-info.java` (`@ApplicationModule(type = Type.OPEN)` +
  `@NullMarked`) est créé ici, sinon `ModularityTests` échoue (« Module platform does not exist! »). `archunit.properties` contient
  `archRule.failOnEmptyShould=false` : dans ce socle, aucune entité, repository ni `@RestController` n'existe encore pour 6 règles de `ArchitectureTests`.

- `Result<T>` : interface `sealed`, `record Success<T>(T value)` et `record Failure<T>(DomainError error)` ; `map`, `flatMap`, `fold`.
- `DomainError` : `record (Kind kind, String code, String message)` ; enum `Kind { VALIDATION, NOT_FOUND, CONFLICT, UNEXPECTED }`.
- `package-info.java` : `@NullMarked` et `@ApplicationModule(type = Type.OPEN)` (voir Q6).

Critères :
- [x] T6 passe : `ResultTest` couvre `map`, `flatMap` et `fold` sur `Success` et sur `Failure` (la fonction n'est pas appelée sur `Failure`), sans exception
- [x] T11 passe : `ModularityTests` (`verify()` et `writes_documentation`)
- [x] T12 passe : `ArchitectureTests`, toutes les règles vertes
- [x] `harness gate arch --rule spring-domain-pure` sort en 0
- [x] `harness gate arch --rule spring-sharedkernel-standalone` sort en 0
- [x] `./gradlew build` sort en 0 (compilation avec NullAway, `spotlessCheck`, tests)

### Étape 3 — platform.config, `application.yml` et sondes Actuator

Fichiers : `src/main/java/com/shop/orders/platform/config/{AppProperties.java, ClockConfig.java, package-info.java}`,
`src/main/resources/application.yml`, `src/test/java/com/shop/orders/platform/config/AppPropertiesTest.java`,
`src/test/java/com/shop/orders/HealthEndpointTest.java`.

- `platform/package-info.java` existe depuis l'étape 2 ; `@NullMarked` sur chaque sous-package.
- `AppProperties` : `record` `@ConfigurationProperties("app")` + `@Validated`, `@NotBlank String serviceName` (Q9) ; activé par
  `@ConfigurationPropertiesScan` ou `@EnableConfigurationProperties` dans `platform.config`. `ClockConfig` : bean `Clock.systemUTC()`.
- `application.yml` = fragment fusionné (Q4, Q5 ; noms de propriétés Jackson vérifiés contre la version de Spring Boot installée, Jackson 3
  avec Boot 4), avec en plus `server.port: 8080`, `app.service-name: shop-orders`, et des sections de profils `local`, `test`, `prod`. Aucun secret.

Critères :
- [x] T10 passe : `AppPropertiesTest` (`ApplicationContextRunner`) : avec `app.service-name` vide, puis absente, le contexte ne démarre pas et le message d'échec contient `service-name` (ou `serviceName`) ; avec `shop-orders`, il démarre
- [x] T1 passe : `HealthEndpointTest` (`@SpringBootTest(RANDOM_PORT)`) : `/actuator/health`, `/actuator/health/liveness` et `/actuator/health/readiness` renvoient 200 avec `status` = `UP`, sans clé `components` (Q7)
- [x] T2 passe : `GET /actuator/env` renvoie 404
- [x] un test lit le `Environment` du contexte démarré et constate `spring.threads.virtual.enabled` = `true` et `server.error.include-stacktrace` = `never`
- [x] `harness gate arch --rule spring-config-safe` sort en 0
- [x] `harness gate arch --rule spring-value-only-in-config` sort en 0
- [x] `harness gate arch --rule spring-platform-no-modules` sort en 0
- [x] `./gradlew build` sort en 0 ; T6, T11 et T12 passent toujours

### Étape 4 — platform.web : format d'erreur unique et corrélation `X-Request-Id`

Fichiers : `src/main/java/com/shop/orders/platform/web/{ApiError.java, Results.java, DomainErrorException.java, GlobalExceptionHandler.java, RequestIdFilter.java, package-info.java}`,
`src/test/java/errorfixture/ErrorFixtureController.java`, `src/test/java/com/shop/orders/platform/web/{GlobalExceptionHandlerTest.java, RequestIdFilterTest.java}`.

- `ApiError` : `record (int statusCode, String code, String message, List<?> details, String traceId)`.
- `GlobalExceptionHandler` : `@RestControllerAdvice` unique, correspondances de Q8 et des décisions ci-dessus. Pour un 500, le message est
  fixe : jamais celui de l'exception, jamais de trace de pile. L'exception est journalisée avec SLF4J.
- `RequestIdFilter` : reprend `X-Request-Id` ou en génère un (UUID), le place dans le MDC (retiré en fin de requête) et le renvoie en en-tête.
- `ErrorFixtureController` (hors `com.shop.orders`, chargé par `@Import`) : routes qui renvoient `Results.unwrap(Failure(...))` pour chaque
  `Kind`, qui lèvent une `IllegalStateException("secret interne")` et qui reçoivent un `@Valid @RequestBody`.

Critères :
- [x] T7 passe : `Failure` de `Kind` `NOT_FOUND`, `VALIDATION` et `CONFLICT` → 404, 400 et 409, corps avec les 5 champs, `code` = code du `DomainError`, `traceId` non vide
- [x] T8 passe : l'exception inattendue → 500 `INTERNAL_ERROR` ; le corps ne contient ni `secret interne`, ni `IllegalStateException`, ni `at ` suivi d'un nom de classe
- [x] un corps de requête invalide → 400 `VALIDATION_ERROR` avec `details` non vide ; une route inconnue → 404 au format commun (et pas 500)
- [x] T9 passe : `X-Request-Id: abc` est renvoyé à l'identique et vaut `traceId` dans un corps d'erreur ; sans en-tête, la réponse porte un `X-Request-Id` non vide
- [x] T2 passe toujours (`/actuator/env` → 404, pas 500)
- [x] `harness gate arch --rule spring-platform-no-modules` sort en 0
- [x] `harness gate arch --rule spring-tests-stack` sort en 0
- [x] `./gradlew build` sort en 0 ; T1, T6, T10, T11 et T12 passent toujours

### Étape 5 — Contrat OpenAPI : exposition, export sans serveur, tâche `openapiExport`

Fichiers : `src/main/resources/application.yml` (section `springdoc`), `src/main/java/com/shop/orders/platform/web/OpenApiConfig.java`,
`src/test/java/com/shop/orders/OpenApiExportTest.java`, `src/test/java/com/shop/orders/DocsUiDisabledTest.java`, `build.gradle.kts`
(tâche `openapiExport`, sans nouvelle dépendance).

- `springdoc.api-docs.path: /docs/openapi.json`, `springdoc.swagger-ui.path: /docs`, `springdoc.swagger-ui.enabled: ${API_DOCS_UI:false}`.
- `OpenApiConfig` : bean `OpenAPI` avec `info.title` = `app.service-name` et une `info.description` non vide.
- `OpenApiExportTest` (MockMvc, sans port ni base) : `GET /docs/openapi.json`, écrit le corps dans `build/openapi.json`, et vérifie que chaque
  entrée de `RequestMappingHandlerMapping` hors springdoc figure dans `paths`.
- `openapiExport` : tâche `Test` filtrée sur `OpenApiExportTest`, jamais à jour (`outputs.upToDateWhen { false }`), dont le `doLast` affiche
  `build/openapi.json` sur la sortie standard.

Critères :
- [x] T3 passe : `GET /docs/openapi.json` → 200, `openapi` commence par `3.`, `info.title` = `shop-orders` ; `build/openapi.json` est écrit
- [x] T4 passe : `DocsUiDisabledTest` : `GET /docs` sans `API_DOCS_UI` → 404
- [x] `./gradlew -q openapiExport` sort en 0 et sa sortie standard est un JSON dont `openapi` commence par `3.` et dont `paths` ne contient aucune route de `ErrorFixtureController`
- [x] `harness gate arch --rule spring-gradle-versions-in-catalog` sort en 0
- [x] `./gradlew build` sort en 0 ; T1, T2, T6 à T12 passent toujours

**Point d'arrêt (humain, règle 5).** Dans un terminal, à la racine : `harness init`. Il détecte le wrapper et la tâche `openapiExport`, puis
déclare la stack `springboot` avec ses commandes `build`, `test`, `lint` et `openapi` dans `harness.config.json`. Collez le diff de
`harness.config.json`.

- [x] (humain) `harness.config.json` déclare une stack avec `build`, `test`, `lint` (`spotlessCheck`) et `openapi` (`./gradlew -q openapiExport`)

### Étape 6 — Contrat commité et runbook local

Fichiers : `docs/api/openapi.json` (généré), `docs/runbooks/local.md`, et (révision, étape bloquée sur `harness gate openapi` : « info.title et
info.version obligatoires », « aucune route documentée ») `build.gradle.kts`, `src/main/java/com/shop/orders/platform/web/OpenApiConfig.java`,
`src/test/java/com/shop/orders/OpenApiExportTest.java`.

- Révision, `info.version` : le projet Gradle n'a pas de version ; `build.gradle.kts` déclare `version = "0.1.0"` (valeur unique, hors catalogue : ce n'est
  pas la version d'une dépendance) et `OpenApiConfig` la reprend dans `Info.version` sans la dupliquer (par exemple `springBoot { buildInfo() }` et le
  bean `BuildProperties`, ou tout mécanisme équivalent qui fonctionne aussi dans `OpenApiExportTest` et `openapiExport`, sans serveur).
- Révision, route documentée (option A) : `OpenApiConfig` documente `GET /docs/openapi.json` depuis le code (`OpenAPI.paths`, `PathItem`,
  `Operation`) : un `summary`, une réponse `200` (description non vide, contenu `application/json`) et une réponse d'erreur (`500`, description non
  vide, schéma `ApiError` du format commun). Aucun contrôleur ajouté ; aucune route de `ErrorFixtureController` dans le contrat.
- `OpenApiExportTest` : vérifie en plus que `info.version` vaut la version du projet et que `paths` contient `/docs/openapi.json` avec les réponses
  `200` et `500` décrites. Sa vérification de couverture des routes (étape 5) reste valable.
- `harness gate openapi --write` génère `docs/api/openapi.json` (jamais écrit à la main).
- `docs/runbooks/local.md` : lancer (`./gradlew bootRun`, profil `local`), tester (`./gradlew test`, `./gradlew build`), activer `/docs`
  (`API_DOCS_UI=true`), régénérer le contrat (`harness gate openapi --write`) et verrouiller après un ajout de dépendance
  (`./gradlew --write-locks dependencies`).

Critères :
- [x] `OpenApiExportTest` passe avec `info.version` = version du projet et `/docs/openapi.json` documenté (réponses `200` et `500` avec descriptions) ; T3 passe toujours
- [x] T5 passe : la commande `openapi` de `harness.config.json` sort en 0 et `harness gate openapi` sort en 0 (sortie identique à `docs/api/openapi.json`)
- [x] T13 passe : `./gradlew spotlessCheck` sort en 0
- [x] `./gradlew build` sort en 0 ; T1, T2 et T6 à T12 passent toujours
- [x] `harness gate arch --prefix spring-` sort en 0
- [x] `docs/runbooks/local.md` contient les commandes de lancement, de test, de régénération du contrat et de verrouillage ci-dessus

### Étape 7 — Révision : `@ApplicationModule(OPEN)` sur le sharedkernel, `domain_is_pure` ciblée

Contexte : `harness verify` échoue sur `springboot-modules.test.mjs` (« sharedkernel : @ApplicationModule absent », « module partagé sans Type.OPEN »),
alors que Q6 avait retiré l'annotation pour satisfaire `domain_is_pure`. Les deux contrôles Harness se contredisent. Décision humaine : corriger le
modèle `ArchitectureTests.java` (correction identique prévue dans Harness v0.7.21 ; pas d'ADR dans ce projet). Q6 est remplacée par cette décision.

Fichiers : `src/test/java/com/shop/orders/ArchitectureTests.java` (règle `domain_is_pure` seule),
`src/main/java/com/shop/orders/sharedkernel/package-info.java`, `docs/runbooks/local.md`.

- `domain_is_pure` : après `.resideInAnyPackage("..domain..", "..application..", "..sharedkernel..")`, ajouter `.and().doNotHaveSimpleName("package-info")`.
  Aucune autre règle ni liste de dépendances interdites ne change : Spring reste interdit dans les classes du domaine, de l'application et du sharedkernel.
- `sharedkernel/package-info.java` : `@NullMarked` et `@ApplicationModule(type = ApplicationModule.Type.OPEN)`.
- `docs/runbooks/local.md` (fichier ajouté à l'étape) : remplacer la commande de verrouillage `./gradlew --write-locks dependencies` (la tâche
  `dependencies` réussit même quand une dépendance ne se résout pas) par la ligne « `harness tool deps` (Harness v0.7.19 ou plus ; ce repo y monte
  juste après ce cycle) ». L'outil est absent de la version installée (0.7.18) : il n'est pas exécuté dans ce cycle.

Critères :
- [x] `git diff src/test/java/com/shop/orders/ArchitectureTests.java` contient exactement deux changements et rien d'autre : l'ajout de `doNotHaveSimpleName("package-info")` dans la clause `that()` de `domain_is_pure`, et la mise au format google-java-format de `no_field_injection` (spotlessApply, étape 2)
- [x] `sharedkernel/package-info.java` porte `@NullMarked` et `@ApplicationModule(type = ApplicationModule.Type.OPEN)`
- [x] T11 et T12 passent (`ModularityTests`, `ArchitectureTests`, toutes les règles vertes)
- [x] `harness gate arch --rule spring-domain-pure` et `--rule spring-sharedkernel-standalone` sortent en 0
- [x] `docs/runbooks/local.md` contient la ligne « `harness tool deps` (Harness v0.7.19 ou plus ; ce repo y monte juste après ce cycle) » et ne contient plus `./gradlew --write-locks dependencies` (vérification par grep)
- [x] le test structurel `springboot-modules.test.mjs` passe (`harness verify`, section structural)
- [x] `./gradlew build` sort en 0 ; `harness gate openapi` reste conforme

T14 (`harness verify` vert avec `build`, `test`, `lint` et `openapi`) est lancé par l'orchestrateur en fin de génération.

## Couverture du plan de test

| Test | Étape |
|---|---|
| T1, T2, T10 | 3 |
| T3, T4 | 5 |
| T5 | 6 |
| T6, T11, T12 | 2 (rejoués ensuite) |
| T7, T8, T9 | 4 |
| T13 | 2 à 6 (`./gradlew build`), 6 explicitement |
| T14 | fin de cycle (orchestrateur) |
