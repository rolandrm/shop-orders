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
