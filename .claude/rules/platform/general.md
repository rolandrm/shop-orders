---
paths:
  - "**/Dockerfile"
  - "**/*.Dockerfile"
  - "**/compose*.yaml"
  - "**/compose*.yml"
  - "environments/**"
  - ".dockerignore"
---

# Plateforme (couche conteneurs) : règles transverses

- **Construire une fois, déployer partout** : une image se construit dans la CI de l'application, sur un tag, et se publie au registre (`ghcr.io/<org>/<app>:<version>`) ; chaque environnement exécute cette même image, jamais une image reconstruite sur la machine cible
- Outillage standard, un par sujet : Docker (BuildKit) pour les images, Docker Compose v2 pour les environnements, GitHub Container Registry pour la publication ; autre outil = écart justifié par une ADR
- Une image et un environnement ne contiennent **aucun secret** : ni dans le Dockerfile (ENV, ARG, COPY d'un `.env`), ni dans un compose commité ; un secret vient de l'exécution (`.env` hors git, secrets de la CI, secrets Docker)
- Toute version est **figée** : image de base, images des services, images des applications ; jamais `:latest`, jamais sans version
- **Un agent écrit l'infrastructure, il ne l'exécute jamais** : construire une image, lancer un conteneur, déployer (local ou distant) sont des gestes humains ; un plan qui en a besoin prévoit un point d'arrêt où l'humain lance la commande et rapporte le résultat
- Une machine Docker peut être locale ou distante : le fichier d'environnement est le même, seul le contexte Docker change (`docker --context <env>`)
- Exception à une règle (conteneur privilégié, socket Docker, réseau de l'hôte) : seulement par une ADR acceptée qui en donne la raison et le périmètre
