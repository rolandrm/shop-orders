# Spec : service-foundation

- **Statut** : Implemented
- **Auteur** : Harness (socle standard Spring Boot), à relire par l'équipe
- **Version** : 0.1.0
- **Liée à** : règles `.claude/rules/springboot/` (general, gradle, configuration, sharedkernel, platform, web, api-docs, testing, verification)

> **À relire avant d'approuver.** Cette spec est fournie par Harness : c'est
> le socle commun à tout backend Spring Boot de la stack, sans
> fonctionnalité métier. Seuls les choix ci-dessous sont propres au projet ;
> modifiez-les ici si besoin, puis approuvez avec
> `/harness:ship docs/specs/service-foundation.md`.
>
> | Choix | Valeur proposée |
> |---|---|
> | Package racine | `com.shop.orders` |
> | Java | 21 (LTS), par la toolchain Gradle : le Java du poste n'a pas d'importance |
> | Build | Gradle, Kotlin DSL, wrapper commité, versions dans `gradle/libs.versions.toml` |
> | Port d'écoute | `8080` |
> | Sonde de santé | `GET /actuator/health` (Actuator, sans dépendance externe) |
> | Base de données | aucune dans ce socle : elle arrive avec la première fonctionnalité qui en a besoin (Flyway, Testcontainers) |

## Contexte

Le dépôt ne contient que Harness : aucune application, aucun build, aucune
commande à vérifier, donc `harness verify` ne vérifie rien. Chaque
fonctionnalité métier aurait besoin des mêmes fondations (build reproductible,
modules isolés, erreurs sous forme de `Result`, configuration validée, contrat
OpenAPI) et les réinventerait. Ce socle les pose une fois, conformes aux
règles de la stack, pour que la première fonctionnalité n'ait plus qu'à
ajouter son module.

## Périmètre

**Inclus** :

- Projet Gradle (Kotlin DSL) à la racine de l'application : `settings.gradle.kts`, `build.gradle.kts` (avec le fragment `build-conventions.gradle.kts` fourni par Harness, fusionné), `gradle/libs.versions.toml` (toutes les versions, aucune en dur), wrapper commité (`gradlew`, `gradle/wrapper/`), toolchain Java 21, `.java-version`, `dependencyLocking { lockAllConfigurations() }` et `gradle.lockfile` commité
- Plugins : Spring Boot, dependency-management, Spotless (google-java-format), Error Prone + NullAway ; dépendances : Spring MVC (`spring-boot-starter-web`), Actuator, Bean Validation, Spring Modulith, springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) ; tests : JUnit 5, AssertJ, ArchUnit, Spring Modulith test ; `jakarta.persistence-api` et `spring-data-commons` en `testImplementation` seulement (les règles de `ArchitectureTests` citent `@Entity` et `Repository` : sans elles, les tests ne compilent pas ; ni JPA ni base à l'exécution)
- `src/main/java/com/shop/orders/Application.java` : `@SpringBootApplication` + `@Modulithic`, threads virtuels activés
- `sharedkernel/` (module OPEN, Java pur) : `Result<T>` (`Success` / `Failure`), `DomainError` (`code`, `message`, `Kind` : `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `UNEXPECTED`) ; `package-info.java` `@NullMarked`
- `platform/` (module OPEN, n'importe aucun module métier) : `config/` (bean `Clock.systemUTC()`, premier `record` `@ConfigurationProperties` + `@Validated` : nom du service), `web/` (`@RestControllerAdvice` unique : `DomainError.Kind` → statut HTTP, erreurs de validation → `400`, toute autre exception → `500`, corps `{ statusCode, code, message, details, traceId }` sans trace de pile ; corrélation `X-Request-Id` dans le MDC et renvoyée en en-tête) ; `package-info.java` `@NullMarked`
- `src/main/resources/application.yml` : fragment `application-architecture.yml` fourni par Harness fusionné ; port `8080` ; Actuator : seuls `health` et `info` exposés, probes liveness et readiness ; Jackson strict ; `server.error.include-stacktrace=never` ; aucun secret ; profils `local`, `test`, `prod`
- Contrat OpenAPI : `springdoc.api-docs.path=/docs/openapi.json` ; Swagger UI sur `/docs` seulement si `API_DOCS_UI=true` (défaut `false`) ; test `OpenApiExportTest` (MockMvc, sans serveur ni base) qui écrit `build/openapi.json` ; tâche Gradle `openapiExport` qui lance ce test puis affiche `build/openapi.json` sur la sortie standard ; contrat généré commité dans `docs/api/openapi.json`
- Tests : `ModularityTests` et `ArchitectureTests` (fournis par `harness init`, placeholders déjà remplacés), premiers tests ci-dessous
- `.gitignore` complété (`build/`, `.gradle/`, `*.iml`, `.idea/`) ; `docs/runbooks/local.md` : lancer (`./gradlew bootRun`), tester, régénérer le contrat
- Commandes de vérification déclarées dans `harness.config.json` : **geste humain** (règle 5). Au point d'arrêt prévu par le plan, l'humain lance `harness init` dans son terminal : il détecte le wrapper et la tâche `openapiExport`, et déclare `build`, `test`, `lint` et `openapi`

**Exclus** :

- Tout module métier (il arrive avec la première fonctionnalité)
- Base de données, JPA, Flyway, Testcontainers
- Sécurité (`SecurityFilterChain`, JWT), client HTTP vers d'autres services, messagerie, tâches planifiées
- Logs JSON et OpenTelemetry
- Dockerfile et image : spec `container-image.md`, après ce socle

## User stories

### US-001 — Le service démarre et dit qu'il est en vie

**En tant qu'** exploitant **je veux** une sonde de santé **afin de** savoir si le service tourne, sans dépendre d'un autre système

```gherkin
Given l'application démarrée
When je demande GET /actuator/health
Then la réponse est 200 avec {"status":"UP"}

Given l'application démarrée
When je demande GET /actuator/health/liveness puis /actuator/health/readiness
Then chacune répond 200 avec {"status":"UP"}

Given l'application démarrée
When je demande GET /actuator/env
Then la réponse est 404 : seuls health et info sont exposés
```

### US-002 — Le contrat d'API est publié et reste fidèle au code

**En tant que** développeur d'une équipe qui consomme le service **je veux** un contrat OpenAPI généré depuis le code **afin de** m'y fier sans lire le code du service

```gherkin
Given l'application démarrée
When je demande GET /docs/openapi.json
Then la réponse est 200 avec un document OpenAPI 3

Given API_DOCS_UI absente
When je demande GET /docs
Then la réponse est 404

Given le code du service
When la commande openapi de harness.config.json est lancée, sans serveur ni base
Then elle écrit sur sa sortie standard le même document que docs/api/openapi.json
```

### US-003 — Une erreur a toujours la même forme

**En tant que** développeur d'une équipe qui consomme le service **je veux** des erreurs au format unique **afin de** les traiter de la même façon partout

```gherkin
Given un cas d'usage qui retourne Failure(DomainError de Kind NOT_FOUND)
When le contrôleur transmet ce résultat
Then la réponse est 404 avec { statusCode, code, message, details, traceId }

Given une exception inattendue dans un contrôleur
When la requête est traitée
Then la réponse est 500 au même format, sans trace de pile ni message interne

Given une requête portant l'en-tête X-Request-Id: abc
When la réponse est renvoyée
Then elle porte X-Request-Id: abc ; sans en-tête, un identifiant est créé et renvoyé
```

### US-004 — Une configuration invalide arrête le démarrage

**En tant qu'** exploitant **je veux** que le service refuse de démarrer sans configuration valide **afin de** ne pas découvrir l'erreur au premier appel

```gherkin
Given le nom du service absent ou vide dans la configuration
When l'application démarre
Then le démarrage échoue avec un message qui nomme la propriété en cause
```

## Contrat d'API

Exposé par l'application :

- `GET /actuator/health` (et `/liveness`, `/readiness`) → `200` `{"status":"UP"}` ; ne dépend d'aucun système externe.
- `GET /docs/openapi.json` → `200`, document OpenAPI 3 du service.

Aucun endpoint métier dans ce socle. Format commun des erreurs :

```json
{ "statusCode": 404, "code": "NOT_FOUND", "message": "…", "details": [], "traceId": "…" }
```

## Contraintes non fonctionnelles

- `./gradlew build` réussit sur un poste qui n'a que Java 17 installé par défaut (toolchain 21, téléchargée ou détectée par Gradle)
- Aucune trace de pile ni message d'exception dans une réponse HTTP
- Aucun secret dans le dépôt ni dans `application.yml`
- Tests sans réseau, sans base, sans serveur démarré sur un port fixe

## Plan de test

| Id | Niveau | Scénario | Attendu | Couvre |
|----|--------|----------|---------|--------|
| T1 | intégration (`@SpringBootTest`, port aléatoire) | `GET /actuator/health`, `/liveness`, `/readiness` | `200` `{"status":"UP"}` pour chacune | US-001 |
| T2 | intégration | `GET /actuator/env` | `404` | US-001 |
| T3 | intégration (MockMvc) | `GET /docs/openapi.json` | `200`, champ `openapi` commençant par `3.` | US-002 |
| T4 | intégration (MockMvc) | `GET /docs` sans `API_DOCS_UI` | `404` | US-002 |
| T5 | outillage | Commande `openapi` de `harness.config.json` | sort en 0 ; `harness gate openapi` vert (sortie identique à `docs/api/openapi.json`) | US-002 |
| T6 | unit | `Result` : `map`, `flatMap`, `fold` sur `Success` et `Failure` | valeurs attendues, aucune exception | US-003 |
| T7 | intégration (MockMvc, contrôleur de test) | `Failure` de `Kind` `NOT_FOUND`, `VALIDATION`, `CONFLICT` | `404`, `400`, `409` au format commun | US-003 |
| T8 | intégration (MockMvc, contrôleur de test) | Exception inattendue | `500` au format commun, sans trace de pile ni message de l'exception | US-003 |
| T9 | intégration (MockMvc) | Requête avec puis sans `X-Request-Id` | même valeur renvoyée ; sinon un identifiant créé et renvoyé | US-003 |
| T10 | intégration (`ApplicationContextRunner`) | Nom du service vide | le contexte ne démarre pas ; message qui nomme la propriété | US-004 |
| T11 | structure | `ModularityTests` | `ApplicationModules.verify()` vert | socle |
| T12 | structure | `ArchitectureTests` | toutes les règles vertes | socle |
| T13 | outillage | `./gradlew spotlessCheck` | sort en 0 | socle |
| T14 | outillage | `harness verify` après la déclaration des commandes | vert, avec `build`, `test`, `lint` et `openapi` exécutés | socle |

Le contrôleur de test de T7 à T9 vit dans `src/test/java` : aucun endpoint
de démonstration dans le code livré.

## Risques

- Versions de Spring Boot, Spring Modulith, springdoc, Error Prone et NullAway incompatibles entre elles : choisir des versions publiées ensemble (la dernière version stable de Spring Boot, et celles que sa documentation ou celle de chaque projet annonce compatibles) ; un écart se voit au build
- La toolchain Java 21 absente du poste : Gradle la télécharge (résolveur `foojay`) ; sans accès réseau, installer Java 21
- NullAway trop strict sur le code généré ou les tests : périmètre limité au package racine, comme le fragment `build-conventions.gradle.kts`
- `ArchitectureTests` qui ne compile pas sans persistance : dépendances de test ci-dessus (pilote shop-orders) ; une constante renommée par une version d'ArchUnit se voit à la compilation des tests
- Le fichier `gradle.lockfile` oublié après un ajout de dépendance : `./gradlew dependencies --write-locks`, sinon le build échoue en CI

## Déploiement et retour arrière

- **Ordre de mise en place** : aucune variable obligatoire hors de `application.yml` dans ce socle ; aucune migration
- **Compatibilité** : Sans objet : première version du service, aucune version précédente en service, aucun consommateur
- **Retour arrière** : redéployer la version précédente ; rien n'est perdu (aucune donnée écrite par le service)
- **Vérification après déploiement** : `GET /actuator/health` renvoie `200` `{"status":"UP"}` et `GET /docs/openapi.json` renvoie le contrat
