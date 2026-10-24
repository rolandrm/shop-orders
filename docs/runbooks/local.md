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

`docker compose up -d --wait` démarre `db` (PostgreSQL), puis `migrate` (tâche à usage unique : Flyway applique le schéma, Hibernate le valide, le conteneur se termine), puis `app`.

Arrêter :

```sh
docker compose down
```

### Joindre shop-api

`app` lit le catalogue de shop-api à l'adresse `SHOP_API_BASE_URL` (obligatoire, sans valeur par défaut : le compose refuse de démarrer sans elle). Lancer shop-api avec son propre compose de développement (port 8081 par défaut dans `.env.example`), puis adapter la variable dans `.env` si besoin. Depuis le conteneur, l'hôte se joint par `host.docker.internal`. `compose.yaml` déclare `extra_hosts: ["host.docker.internal:host-gateway"]` parce que Docker Engine sous Linux ne définit pas ce nom (Docker Desktop le fait) ; l'entrée est sans effet sous Docker Desktop.

### Appliquer le seed

Données de démonstration (2 commandes d'un client de démonstration), jamais en production. Elles s'appliquent avec l'image de migration, sur la base démarrée :

```sh
docker compose run --rm migrate java -jar /app/app.jar --spring.profiles.active=migrate,seed
```

Le seed est idempotent : le rejouer ne change pas les données.

### Recréer la base

```sh
docker compose down -v
docker compose up -d --wait
```

`-v` supprime le volume nommé de la base (schéma et données).

### Régénérer le contrat de shop-api

Geste humain, quand shop-api publie un nouveau contrat :

```sh
harness tool contract-pull shop-api
```

Puis relancer les tests de conformité au contrat (`./gradlew integrationTest`).

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

`image-check` couvre l'image du service et l'image `-migrate`, `compose-check` lance aussi le seed. Le retour arrière (`rollback-check`) se prouve dans shop-platform à la PR de version 0.2.0 (N-1 = 0.1.0, sans base).
