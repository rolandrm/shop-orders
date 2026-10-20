---
paths:
  - "**/Dockerfile"
  - "**/*.Dockerfile"
  - "**/Dockerfile.*"
  - "**/.dockerignore"
---

# Dockerfile d'une application

- Multi-étapes : dépendances, construction, puis une étape finale minimale qui ne contient que l'exécutable et ses dépendances de production (ni sources, ni outils de build, ni dépendances de développement)
- Image de base versionnée (`node:22.11-alpine`, `eclipse-temurin:21-jre-alpine`), au mieux figée par empreinte (`@sha256:…`) ; jamais `:latest` ni sans version
- L'étape finale tourne sous un **utilisateur non root, désigné par son identifiant numérique** (`USER 1000:1000`, l'utilisateur `node` des images Node) : un orchestrateur ne vérifie « non root » que sur un identifiant numérique (hadolint DL3066) ; fichiers copiés avec `--chown`
- Dépendances de production seulement dans l'étape finale : vérifier dans l'image construite qu'aucun outil de développement n'y est entré par les dépendances « pairs » (pnpm `autoInstallPeers` ; pilote shop-api : CLI Prisma et TypeScript dans l'image)
- `.dockerignore` à côté du Dockerfile : exclut au minimum `.env`, `.env.*`, `.git`, `node_modules`, les sorties de build ; sans lui, tout le dossier (secrets compris) part dans le contexte de build
- `COPY`, jamais `ADD` depuis une URL ; jamais `curl … | sh` : télécharger, vérifier l'empreinte, puis exécuter
- Aucun secret en `ENV` ou `ARG` (lisible dans `docker history`) ; secret de build par `RUN --mount=type=secret`
- `HEALTHCHECK` qui interroge la sonde de santé de l'application (sans outil absent de l'image : `node -e "fetch(…)"` plutôt que `curl` sur une image qui n'en a pas) ; ou sonde déclarée dans chaque compose qui l'exécute
- `CMD` / `ENTRYPOINT` en forme exec (`["node", "dist/main.js"]`) : l'application reçoit les signaux d'arrêt et s'arrête proprement ; un seul processus par conteneur ; journaux sur stdout/stderr
- Migrations de base de données : jamais au démarrage implicite de l'application ; commande séparée (service `migrate` à usage unique, lancé avant l'application), compatible avec la version précédente
- Étiquettes OCI : `org.opencontainers.image.source`, `.version`, `.revision` (renseignées par la CI)
