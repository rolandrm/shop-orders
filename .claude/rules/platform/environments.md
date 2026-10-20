---
paths:
  - "environments/**"
  - "docs/runbooks/**"
---

# Environnements et déploiement (repo plateforme)

- Un environnement = un dossier `environments/<nom>/` : `compose.yaml` (les versions qui y tournent), `.env.example` (variables attendues, sans valeur), `README.md` (machine, contexte Docker, accès) ; le `.env` réel vit sur la machine ou dans les secrets de la CI, jamais dans git
- La version déployée se lit dans le `compose.yaml` commité : déployer une nouvelle version = une PR qui change un tag d'image, relue, fusionnée sur CI verte ; le changement se fait par `harness tool env-set <app> X.Y.Z` (vérifie que chaque image est publiée, change l'application et sa migration ensemble), jamais à la main
- Livrer n'est pas déployer : une version publiée par la CI de l'application (tag, `release-check` vert) ne tourne nulle part tant que la PR de version de ce repo n'est pas fusionnée ; jamais une PR de version avant le `release-check` vert
- Les versions ne se recopient pas dans la documentation (`README.md`, runbooks) : elle renvoie au `compose.yaml` ; un tag recopié doit être celui du compose (contrôle structurel)
- Machine distante : un contexte Docker par environnement (`docker context create staging --docker "host=ssh://deploy@staging.example.com"`), jamais le socket Docker exposé sur le réseau ; utilisateur de déploiement dédié, accès par clé
- Déployer (geste humain, terminal ou CI déclenchée à la main) :
  `docker --context <env> compose -f environments/<env>/compose.yaml --env-file <.env> pull`
  puis `… up -d --wait` (attend que chaque service soit sain)
- Migration d'une application : son image publiée `ghcr.io/<org>/<app>-migrate:X.Y.Z` (déclarée par `publish.extra` dans le repo de l'application), toujours à la même version que l'application ; jamais une image construite ici
- Retour arrière : revenir au commit précédent du `compose.yaml` (les tags d'images précédents) et relancer `pull` puis `up -d --wait` ; une migration de base doit rester compatible avec la version précédente de l'application, sinon le retour arrière est décrit dans le runbook avant le déploiement. Preuve avant la fusion d'une PR de version : `harness tool rollback-check` (N-1, N, puis retour à N-1 sur la base migrée, données comptées). Les données de test viennent du seed que l'application déclare dans son image (`io.harness.seed`), jamais d'un fichier écrit ici au format d'une autre équipe ; `--sql` seulement pour une base sans seed
- Vérification après déploiement : `docker --context <env> compose … ps` (tous `healthy`) et la sonde de santé publique de chaque application
- Données de démonstration (`io.harness.seed`) : toujours sur `local` ; sur un environnement partagé seulement si `environments/<env>/harness.json` le déclare (`{ "seed": true }`, décision relue en PR) ; jamais sur `staging`, `preprod`, `prod`, `production` (refusé par `env-up`). Jamais de données de production hors de la production
- Adresses publiques d'un environnement distant (reverse proxy) : `"urls": { "<app>": "https://…" }` dans `environments/<env>/harness.json` ; ce sont elles que les fronts déclarent
- Déploiement continu : dans `harness.config.json` (règle 5), puis `harness ci` : un job par environnement lance `env-up --env <env>` à chaque push sur main (à la main seulement pour recette et production). Deux façons de joindre la machine : `"deploy": { "<env>": { "runner": "self-hosted" } }`, un runner GitHub installé sur la machine (aucune connexion entrante, dépôt privé seulement, étiquette = nom de l'environnement, autre par `"labels"`) ; ou `"deploy": { "<env>": { "host": "<utilisateur>@<machine>" } }`, connexion SSH par une clé dédiée restreinte à `docker system dial-stdio`, empreinte vérifiée. Dans les deux cas, `.env` en secret GitHub (`HARNESS_<ENV>_ENV`), effacé en fin de job
- Chaque environnement a son runbook (`docs/runbooks/<env>.md`) : déployer, revenir en arrière, consulter les journaux, restaurer une sauvegarde
