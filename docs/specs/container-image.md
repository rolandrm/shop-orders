# Spec : container-image

- **Statut** : Implemented
- **Auteur** : Harness (socle standard plateforme, couche conteneurs), à relire par l'équipe
- **Version** : 0.1.0
- **Liée à** : règles `.claude/rules/platform/` (general, dockerfile, compose, publishing, verification)

> **À relire avant d'approuver.** Cette spec est fournie par Harness : l'image
> de production d'une application, identique pour toutes les stacks. Seuls les
> choix ci-dessous sont propres au projet ; modifiez-les ici si besoin, puis
> approuvez avec `/harness:ship docs/specs/container-image.md`.
>
> | Choix | Valeur proposée |
> |---|---|
> | Image publiée | `ghcr.io/rolandrm/shop-orders` |
> | Port d'écoute dans le conteneur | `8080` |
> | Sonde de santé (HTTP, sans dépendance externe) | `GET /actuator/health` |
> | Base de données | aucune détectée (ni ORM, ni migrations, ni URL de base) : compose sans base ni migration ; à changer ici si l'équipe en ajoute une |
> | Image de base | la version LTS de l'environnement d'exécution de la stack, en variante minimale (`-alpine` ou `-slim`), version figée |

## Contexte

L'application n'a pas d'image de production : on ne peut ni la lancer en une
commande ailleurs que sur un poste de développement, ni la déployer. Une image
construite une fois par la CI, publiée au registre et exécutée telle quelle
dans chaque environnement, local ou distant, rend chaque déploiement
reproductible et chaque retour arrière possible.

## Périmètre

**Inclus** :

- `Dockerfile` multi-étapes à la racine de l'application : dépendances, construction, étape finale minimale (exécutable et dépendances de production seulement), utilisateur non root, `HEALTHCHECK` sur la sonde de santé, `CMD` en forme exec
- `.dockerignore` : `.git`, `.env`, `.env.*`, dépendances, sorties de build, rapports de tests
- Sonde de santé `GET /actuator/health` : répond `200` sans appeler de dépendance externe ; ajoutée si l'application n'en a pas
- Configuration lue à l'exécution (variables d'environnement), jamais dans l'image ; démarrage refusé si une variable obligatoire manque
- `compose.yaml` de développement à la racine : l'application construite localement (`build: .`) et ses dépendances (avec une base de données, selon le tableau en tête : la base et une migration à usage unique avant l'application), pour lancer le tout en une commande ; `.env.example` avec les variables attendues ; avec une base, l'étape de migration porte l'étiquette `io.harness.seed` (commande du seed de démonstration), utilisée par `harness tool rollback-check` dans le repo plateforme
- Déclaration de la publication dans `harness.config.json` : `"publish": { "image": "ghcr.io/rolandrm/shop-orders" }` (geste humain, règle 5), puis `harness ci` : construction de l'image sur chaque PR, publication `ghcr.io/rolandrm/shop-orders:X.Y.Z` sur chaque tag vérifié
- `docs/runbooks/local.md` : lancer, arrêter, consulter les journaux, repartir de zéro

**Exclus** :

- Environnements partagés (test, préproduction, production) : repo plateforme, spec `environment`
- Kubernetes, Helm, orchestration
- Images multi-architectures
- Registre autre que GitHub Container Registry

## User stories

### US-001 — Une image de production qui démarre saine

**En tant que** équipe plateforme **je veux** une image de l'application qui démarre et se déclare saine **afin de** la déployer sans connaître son code

```gherkin
Given l'image construite par docker build
When elle démarre avec une configuration valide
Then sa sonde de santé répond 200 et docker la marque healthy
And son processus ne tourne pas en root
```

### US-002 — Lancer l'application et ses dépendances en une commande

**En tant que** développeur **je veux** lancer l'application et ses dépendances (sa base et ses migrations, s'il y en a une) en une commande **afin de** travailler sans procédure à reconstituer

```gherkin
Given un poste avec Docker et un fichier .env copié de .env.example
When je lance docker compose up -d --wait
Then chaque service est healthy et l'application répond sur son port publié
And avec une base de données, les migrations ont été appliquées avant le démarrage de l'application
```

### US-003 — Aucune configuration ni aucun secret dans l'image

**En tant que** responsable sécurité **je veux** une image sans secret **afin que** sa fuite ou sa publication ne révèle rien

```gherkin
Given l'image construite
When on inspecte son historique et ses variables (docker history, docker inspect)
Then aucun mot de passe, jeton ni fichier .env n'y figure

Given l'image lancée sans une variable obligatoire
When elle démarre
Then elle s'arrête avec un message qui nomme la variable manquante
```

### US-004 — Image publiée sur chaque version vérifiée

**En tant que** équipe plateforme **je veux** une image publiée par version **afin de** déployer et revenir en arrière par numéro de version

```gherkin
Given un tag vX.Y.Z dont harness verify est vert en CI
When la CI de l'application tourne sur ce tag
Then l'image ghcr.io/rolandrm/shop-orders:X.Y.Z est publiée au registre

Given une pull request qui modifie le Dockerfile
When la CI tourne
Then l'image est construite, sans être publiée
```

## Contrat d'API

Exposé : `GET /actuator/health` → `200` quand l'application est prête ; aucune
dépendance externe interrogée. Aucun autre changement d'API.

## Contraintes non fonctionnelles

- Sécurité : utilisateur non root ; aucun secret dans l'image ni dans un compose commité ; image de base figée
- Taille : étape finale sans outils de build ni dépendances de développement
- Démarrage : sonde de santé verte en moins de 30 secondes après le lancement
- Reproductibilité : même tag de version, même image, dans chaque environnement

## Plan de test

| Id | Niveau | Scénario | Attendu | Couvre |
|----|--------|----------|---------|--------|
| T1 | structure | Invariants plateforme et contrôles structurels (les règles de `environments/` ne concernent que le repo plateforme : sans fichier ici) | `harness gate arch --prefix platform-` sort en 0 ; `harness verify` vert | US-001, US-003 |
| T2 | outillage | Lint du Dockerfile (si hadolint est installé) | `hadolint Dockerfile` sort en 0 | US-001 |
| T3 | humain | Image de production | `harness tool image-check --build --requires-env` : construite ; utilisateur non root ; sonde de santé ; aucun secret, aucune valeur ni fichier `.env` ; aucun outil de développement ; refus de démarrer sans configuration (le journal nomme la variable) | US-001, US-003 |
| T4 | humain | Lancement complet | `harness tool compose-check` : compose valide, tous les services healthy ; avec une base, migration terminée avec le code 0 avant l'application, puis seed de démonstration (`io.harness.seed`) lancé dans l'image ; tout supprimé ensuite | US-001, US-002 |
| T5 | humain | Livraison du premier tag | `harness tool release-check vX.Y.Z` : tag sur main, image au registre (et contrat publié s'il est déclaré) | US-004 |
| T6 | structure | Construction en CI sur PR, publication sur tag | le workflow généré par `harness ci` contient la construction de l'image et sa publication sur `v*` | US-004 |
| T7 | unit | Sonde de santé | `GET /actuator/health` répond 200 sans dépendance externe (test de la stack) | US-001 |

Les tests marqués « humain » se font à un point d'arrêt du plan : Docker est
refusé aux agents. L'humain lance la commande indiquée dans un terminal et
colle son rapport (✅ / ❌, avec la correction de chaque échec) en réponse au
point d'arrêt ; aucune commande Docker à composer à la main.

## Risques

- Outils absents de l'image finale (`curl` pour la sonde) : sonde écrite avec l'environnement d'exécution de l'application
- Avec une base : migrations lancées au démarrage de chaque réplique, donc en concurrence ; d'où le service de migration à usage unique
- Différence entre le build local et celui de la CI (architecture du processeur) : la CI fait foi
- Fichier `.env` copié dans l'image faute de `.dockerignore` : contrôlé par le test structurel

## Déploiement et retour arrière

- **Ordre de mise en place** : sonde de santé et Dockerfile, puis compose de développement, puis déclaration de la publication et `harness ci`, puis premier tag
- **Compatibilité** : Sans objet : aucune image publiée auparavant
- **Retour arrière** : revenir au tag d'image précédent ; rien n'est perdu (l'image ne contient aucune donnée)
- **Vérification après déploiement** : l'image `ghcr.io/rolandrm/shop-orders:X.Y.Z` existe au registre et démarre healthy
