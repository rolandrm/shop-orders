---
paths:
  - "**/Dockerfile"
  - ".github/workflows/**"
---

# Publication des images (repo d'une application)

- L'image se publie par la CI, sur un tag vérifié (`vX.Y.Z`, `harness verify` vert) : `ghcr.io/<org>/<app>:X.Y.Z` ; jamais depuis un poste
- Un tag d'image publié ne change jamais : republier la même version est refusé, une correction est une nouvelle version
- Chaque PR prouve que l'image se construit (`docker build` en CI, sans publication) : un Dockerfile cassé ne doit pas se découvrir au tag
- Déclaré dans `harness.config.json` : `"publish": { "image": "ghcr.io/<org>/<app>" }` ; `harness ci` ajoute la construction (PR) et la publication (tag)
- Tâche qui ne doit pas alourdir l'image de l'application (migration avec son CLI, initialisation) : une étape du Dockerfile (`FROM build AS migrate`) publiée à part, `"publish": { "image": …, "extra": { "migrate": { "target": "migrate" } } }` → `ghcr.io/<org>/<app>-migrate:X.Y.Z`, à la même version ; un environnement la lance avant l'application
- Données de démonstration (seed) d'une application avec une base : déclarées sur l'étape de migration par l'étiquette `LABEL io.harness.seed="<commande>"` (ex. `NODE_ENV=development pnpm db:seed` quand le seed refuse la production) ; `harness tool compose-check` la lance dans l'image construite (preuve qu'elle tourne sous l'utilisateur de l'image), `harness tool rollback-check` celle de la version précédente pour prouver qu'aucune donnée n'est perdue. Jamais exécutée en production ; le seed garde son refus de `NODE_ENV=production`
- Étiquettes OCI (`org.opencontainers.image.source`, `.version`, `.revision`) : posées par la CI à la publication (`docker build --label`) ; le Dockerfile ne déclare ni `LABEL` ni `ARG VERSION` / `ARG REVISION` pour elles (pilote shop-web : le plan les croyait transmises par la CI, elles seraient restées vides)
