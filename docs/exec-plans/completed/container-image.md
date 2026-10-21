# Plan : container-image

- **Statut** : COMPLETED
- **Taille** : M
- **Durée attendue** : 174 min (médiane de 1 cycle M sur ce poste)
- **Points d'arrêt** : après l'étape 3 (première construction de l'image et premier lancement du compose, gestes Docker humains ; ajout de `publish` dans `harness.config.json`, règle 5 ; régénération du workflow par `harness ci --force` dans le terminal de l'humain, règle 4)
  **Note** : l'humain approuve ce plan en tapant `/harness:ship docs/specs/container-image.md --continue` ; il passe `COMPLETED` sur preuve en fin de cycle (toutes les cases cochées, `harness verify` vert).

- **Spec** : `docs/specs/container-image.md` (Approved, v0.1.0)

## Questions à l'humain

aucune (les 8 questions ont reçu leur réponse, reportée ci-dessous et dans les étapes)

## Décisions

1. **Images de base figées** : `eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24` pour l'étape de construction, `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24` pour l'étape d'exécution (Alpine figée elle aussi). Les deux `FROM` de l'étape 2 portent exactement ces tags.
2. **Variable obligatoire** : oui. `APP_SERVICE_NAME` devient obligatoire (placeholder `${APP_SERVICE_NAME:}` dans le document de base, `@NotBlank` dont le message nomme `APP_SERVICE_NAME`) ; `shop-orders` reste la valeur du profil `local` et des tests. Le titre du contrat OpenAPI ne change pas (étape 1).
3. **Sonde `HEALTHCHECK`** : oui, `wget -q -O /dev/null http://127.0.0.1:8080/actuator/health` (busybox d'Alpine) (étapes 2 et 3).
4. **`.gitignore`** : oui, ajout de `.env`, `.env.*` et `!.env.example` (étape 2).
5. **hadolint** : non installé sur ce poste. T2 est sauté (critère retiré de l'étape 2) ; `harness verify` en fin de cycle fait les contrôles structurels. Aucune déclaration dans `harness.config.json`.
6. **Déclaration de publication** : oui, l'humain ajoute lui-même `"publish": { "image": "ghcr.io/rolandrm/shop-orders" }` dans `harness.config.json` au point d'arrêt (règle 5). Pas d'extra `migrate`.
7. **Workflow CI** : non pour l'agent. `harness ci` refuse de tourner sous un agent (écriture dans `.github/workflows/`, règle 4). Au point d'arrêt de l'étape 3, l'humain ajoute `publish` puis lance `harness ci --force` dans son terminal. L'étape 4 ne lance pas `harness ci` et n'écrit aucun fichier : elle vérifie seulement le contenu du workflow généré.
8. **T5** : oui, hors plan, au premier tag après la fusion ; décrit dans le runbook.

## Constat sur l'existant

- La sonde de santé existe déjà : `GET /actuator/health` (actuator, probes activées), prouvée par `src/test/java/com/shop/orders/HealthEndpointTest.java` (`healthEndpointsAreUpWithoutComponents` : 200, `UP`, sans `components`). T7 est donc couvert. Aucun code de sonde n'est à écrire, seul ce test doit continuer à passer.
- Aucune base de données : pas de service `db` ni `migrate`, pas d'étiquette `io.harness.seed`.
- Port `8080` (`server.port` dans `application.yml`), JDK 21 (`.java-version`, `gradle/libs.versions.toml`), Spring Boot 4.1.1, artefact produit par `./gradlew bootJar`, version `0.1.0` (`build.gradle.kts`).
- Invariants : `platform-environment-no-build` ne vise que `environments/` (aucun fichier ici). Les critères utilisent donc `harness gate arch --prefix platform-`, jamais `--rule platform-environment-no-build`.

## Étapes

### Étape 1 : `APP_SERVICE_NAME` obligatoire, démarrage refusé en la nommant (US-003, T7)

Fichiers :
- `src/main/resources/application.yml` : document de base `app.service-name: ${APP_SERVICE_NAME:}`, et `app.service-name: shop-orders` dans le document du profil `local`
- `src/main/java/com/shop/orders/platform/config/AppProperties.java` : message de `@NotBlank` qui nomme `APP_SERVICE_NAME`
- `src/test/resources/config/application.yml` (nouveau) : `app.service-name: shop-orders` pour les tests Spring (`HealthEndpointTest`, `DocsUiDisabledTest`, `OpenApiExportTest`)
- `src/test/java/com/shop/orders/platform/config/AppPropertiesTest.java` : les deux tests d'échec vérifient aussi `APP_SERVICE_NAME` dans la trace
- `src/test/java/com/shop/orders/RequiredEnvironmentTest.java` (nouveau) : démarre `Application` avec `spring.config.location=classpath:/application.yml` (pour ignorer la config de test), `server.port=0`, sans `APP_SERVICE_NAME`

Critères :
- [x] `RequiredEnvironmentTest` passe : sans `APP_SERVICE_NAME`, le démarrage de `Application` sur le vrai `application.yml` échoue et l'exception contient `APP_SERVICE_NAME`. Ce test échouerait si une valeur par défaut restait dans le document de base.
- [x] `RequiredEnvironmentTest` passe aussi dans le sens positif : avec `APP_SERVICE_NAME=shop-orders` passé en propriété, le contexte démarre
- [x] `AppPropertiesTest` passe : `blankServiceNameFailsContext` et `missingServiceNameFailsContext` trouvent `APP_SERVICE_NAME` dans la trace, et `validServiceNameStarts` reste inchangé
- [x] `HealthEndpointTest`, `DocsUiDisabledTest`, `OpenApiExportTest`, `ModularityTests` et `ArchitectureTests` passent sans modification (T7 : `/actuator/health` répond 200 sans dépendance externe)
- [x] `harness gate openapi` sort en 0 (titre du contrat toujours `shop-orders`)
- [x] `harness gate arch --rule spring-value-only-in-config` sort en 0
- [x] `./gradlew build -x test`, `./gradlew test` et `./gradlew spotlessCheck` sortent en 0

### Étape 2 : `Dockerfile` multi-étapes, `.dockerignore`, `.gitignore` (US-001, US-003, T1)

Fichiers :
- `Dockerfile` (nouveau, racine). Étape `build` sur `eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24` (décision 1) : copie d'abord `gradlew`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle.lockfile`, `settings-gradle.lockfile`, résolution des dépendances, puis `src/` et `./gradlew bootJar --no-daemon`. Étape finale sur `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24` : seulement le jar (`COPY --from=build --chown=1000:1000`), `USER 1000:1000`, `EXPOSE 8080`, `HEALTHCHECK` avec `--start-period` d'au plus 30 s et la commande `wget -q -O /dev/null http://127.0.0.1:8080/actuator/health` (décision 3), `CMD ["java", "-jar", "/app/app.jar"]` en forme exec. Ni `ENV` ni `ARG` de configuration ou de secret. Pas de `LABEL` OCI ni d'`ARG VERSION`/`REVISION` : la CI les pose (règle publishing).
- `.dockerignore` (nouveau) : `.git`, `.env`, `.env.*`, `.gradle`, `build`, `.idea`, `*.iml`, `.claude`, rapports de tests
- `.gitignore` : ajout de `.env`, `.env.*`, `!.env.example` (décision 4)

Critères :
- [x] `harness gate arch --prefix platform-` sort en 0 (images figées, pas d'`ADD` d'URL, pas de pipe vers un shell, pas de secret dans l'image)
- [x] `harness gate arch --rule platform-no-latest-image` et `harness gate arch --rule platform-no-secret-in-image` sortent en 0
- [x] Le `Dockerfile` a exactement deux `FROM` : `eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24` (étape `build`) et `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24` (étape finale)
- [x] L'étape finale du `Dockerfile` contient `USER` avec un identifiant numérique, un `HEALTHCHECK` sur `/actuator/health` du port 8080, et un `CMD` en forme exec (tableau JSON)
- [x] `.dockerignore` contient les entrées `.env`, `.env.*`, `.git`, `build` et `.gradle`
- [x] `.gitignore` contient `.env`, `.env.*` et `!.env.example`
- [x] `./gradlew build -x test` et `./gradlew test` sortent toujours en 0 (aucune régression)

T2 (`hadolint Dockerfile`) est sauté : hadolint n'est pas installé sur ce poste (décision 5).

### Étape 3 : `compose.yaml` de développement, `.env.example`, runbook (US-002)

Fichiers :
- `compose.yaml` (nouveau, racine). Un seul service `app` : `build: .`, `environment: APP_SERVICE_NAME: ${APP_SERVICE_NAME:?APP_SERVICE_NAME manquante}`, `ports: "127.0.0.1:${APP_PORT:-8080}:8080"`, `healthcheck` (même sonde `wget` que le Dockerfile), `restart: unless-stopped`, `mem_limit` ou `deploy.resources.limits`. Pas de base ni de migration (tableau de la spec).
- `.env.example` (nouveau) : `APP_SERVICE_NAME=shop-orders`, `APP_PORT=8080`, sans valeur secrète
- `docs/runbooks/local.md` (nouveau). Prérequis, puis `cp .env.example .env` et `docker compose up -d --wait`. Ensuite arrêter (`docker compose down`), consulter les journaux (`docker compose logs -f app`), repartir de zéro (`docker compose down -v --rmi local` puis `up --build`). Il décrit aussi les preuves `harness tool image-check --build --requires-env` et `harness tool compose-check`, et le contrôle après le premier tag (`harness tool release-check vX.Y.Z`, décision 8).

Critères :
- [x] `harness gate arch --prefix platform-` sort en 0 (compose sans `latest`, sans secret littéral, sans `privileged` ni socket Docker)
- [x] `harness gate arch --rule platform-compose-no-secret-literal` et `harness gate arch --rule platform-compose-no-latest-image` sortent en 0
- [x] Chaque variable référencée dans `compose.yaml` figure dans `.env.example`
- [x] Le port publié de `compose.yaml` est lié à `127.0.0.1` et configurable par `APP_PORT`
- [x] `docs/runbooks/local.md` contient les quatre gestes (lancer, arrêter, journaux, repartir de zéro) et les commandes de preuve `image-check`, `compose-check`, `release-check`

**Point d'arrêt (humain)**, après l'étape 3. Dans un terminal, à la racine :
1. `harness tool image-check --build --requires-env` (T3 : construite, non root, sonde saine, aucun secret ni `.env`, aucun outil de développement, refus de démarrer sans `APP_SERVICE_NAME` nommée dans le journal) : coller le rapport
2. `harness tool compose-check` (T4 : compose valide, service `app` healthy, tout supprimé ensuite) : coller le rapport
3. Ajouter `"publish": { "image": "ghcr.io/rolandrm/shop-orders" }` dans `harness.config.json`, au niveau de la stack ou à l'endroit indiqué par `harness ci --help` (décision 6)
4. Lancer `harness ci --force`, qui régénère `.github/workflows/harness.yml` avec la construction sur PR et la publication sur tag (décision 7 : refusé sous un agent, règle 4) : signaler qu'il sort en 0

Chaque rapport doit finir par « ✅ Tous les contrôles passent ». Chaque ❌ est corrigé selon le rapport (retour à l'étape 2 ou 3) avant l'étape 4.

### Étape 4 : vérification du workflow CI, construction sur PR, publication sur tag (US-004, T6)

Prérequis : `harness.config.json` contient `publish.image = "ghcr.io/rolandrm/shop-orders"` et `.github/workflows/harness.yml` a été régénéré par l'humain avec `harness ci --force` (point d'arrêt). Sinon, s'arrêter et le signaler : c'est un blocage, pas un écart à corriger.

Fichiers : aucun écrit. Lecture seule de `.github/workflows/harness.yml` (généré par l'humain, règle 4). Si un critère échoue, s'arrêter et le signaler à l'humain, sans retoucher le workflow.

Critères :
- [x] `.github/workflows/harness.yml` contient une construction de l'image (`docker build` ou action équivalente) exécutée sur `pull_request`, sans publication
- [x] `.github/workflows/harness.yml` contient une publication vers `ghcr.io/rolandrm/shop-orders` déclenchée sur les tags `v*`, conditionnée à la réussite de `harness verify`, avec `packages: write` limité à ce job
- [x] Le job `verify` existant (`harness verify --summary` sur PR et sur `main`) est toujours présent

## Hors plan

- T2 (`hadolint Dockerfile`) : sauté, hadolint non installé sur ce poste (décision 5)
- T5 (`harness tool release-check vX.Y.Z`) : après la fusion et le premier tag (décision 8), décrit dans `docs/runbooks/local.md`
- Régénération du workflow (`harness ci --force`) : geste humain au point d'arrêt (décision 7)
- `harness verify` complet : lancé par l'orchestrateur en fin de génération (T1)
