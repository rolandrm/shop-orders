---
paths:
  - "**/compose*.yaml"
  - "**/compose*.yml"
  - "**/docker-compose*.yaml"
  - "**/docker-compose*.yml"
---

# Fichiers compose

- Chaque service : une image **publiée et versionnée** (`ghcr.io/org/shop-api:1.0.0`, `postgres:16.4-alpine`), une `healthcheck`, une politique `restart` ; un `compose.yaml` de développement dans le repo d'une application peut faire `build:`, jamais un environnement
- Ordre de démarrage par la santé : `depends_on: { db: { condition: service_healthy } }`, et `service_completed_successfully` pour une migration à usage unique
- Secrets référencés, jamais écrits : `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}` ; valeurs dans un `.env` hors git, à côté du compose, dont un `.env.example` commité donne la liste sans valeur réelle
- Données dans des volumes nommés ; aucun montage de `/var/run/docker.sock`, aucun `privileged`, `network_mode: host` ou `pid: host`
- Ports : n'exposer que ce que l'extérieur appelle ; un service interne (base, cache) n'a pas de `ports:` ou se lie à `127.0.0.1` ; tout port publié est configurable (`"127.0.0.1:${DB_PORT:-5432}:5432"`, variable listée dans `.env.example`) : sur un poste, le port par défaut est souvent déjà pris (pilote shop-api : un autre PostgreSQL sur 5432)
- Tâche à usage unique (migration, initialisation) : `restart: "no"`, attendue par `depends_on: { … : { condition: service_completed_successfully } }` ; elle n'a pas de sonde de santé
- Limites de ressources (`mem_limit`, `cpus`, ou `deploy.resources.limits`) sur les services applicatifs
- `docker compose config` doit sortir en 0 : il valide la syntaxe et les variables référencées
