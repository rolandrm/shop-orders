# Runbook local

## Lancer

```sh
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Sondes : `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`.

## Tester

```sh
./gradlew test    # tests unitaires et verrous d'architecture
./gradlew build   # compilation, spotlessCheck, tests
```

## Activer la documentation interactive `/docs`

```sh
API_DOCS_UI=true SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Sans `API_DOCS_UI=true`, `/docs` répond 404. Ne jamais l'activer en production.

## Régénérer le contrat OpenAPI

```sh
harness gate openapi --write
```

Le contrat `docs/api/openapi.json` n'est jamais édité à la main ; il est commité avec le code.

## Verrouiller les dépendances

Après tout ajout ou changement de dépendance :

`harness tool deps` (Harness v0.7.19 ou plus ; ce repo y monte juste après ce cycle)

## Image et compose

Prérequis : Docker avec le plugin compose.

Lancer :

```sh
cp .env.example .env
docker compose up -d --wait
```

Arrêter :

```sh
docker compose down
```

Consulter les journaux :

```sh
docker compose logs -f app
```

Repartir de zéro :

```sh
docker compose down -v --rmi local
docker compose up -d --wait --build
```

## Preuves

```sh
harness tool image-check --build --requires-env   # image : non root, sonde, aucun secret, refus sans APP_SERVICE_NAME
harness tool compose-check                        # compose valide, service app healthy, tout supprimé ensuite
harness tool release-check vX.Y.Z                 # après la fusion et le premier tag : tag sur main, image au registre
```
