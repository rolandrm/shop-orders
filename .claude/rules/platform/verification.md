---
paths:
  - "**/Dockerfile"
  - "**/compose*.yaml"
  - "**/compose*.yml"
  - "environments/**"
---

# Vérifier une modification d'infrastructure

- `harness verify` : invariants `platform-*` (versions figées, secrets, conteneurs privilégiés, environnements sans build) et contrôles structurels (base versionnée, utilisateur non root, `.dockerignore`, sondes de santé)
- Outils du métier, déclarés comme commandes de la stack quand ils sont installés : `hadolint <Dockerfile>` (lint), `docker compose -f <fichier> config --quiet` (validité)
- Ce qu'un agent ne prouve pas seul (Docker lui est refusé) : que l'image se construit et que les conteneurs démarrent sains. Le plan le confie à un point d'arrêt humain (`harness tool image-check --build --requires-env`, `harness tool compose-check`, rapport collé tel quel), ou à la construction en CI sur la PR
- Critère d'une image : construite, démarre, sa sonde de santé répond, elle tourne sous un utilisateur non root, sans secret ni outil de développement : tout ce que vérifie `harness tool image-check`
- Image de base : la figer à la version complète que donne `harness tool image-version <image:tag>` (jamais un tag mobile), à la majeure d'exécution testée par la CI
