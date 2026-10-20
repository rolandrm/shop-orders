# <Nom du projet>

Ce repo utilise le plugin **harness** (agents, hooks, gates, evals).
Les commandes du harness sont préfixées : `/harness:ship`, `/harness:plan`,
`/harness:spec`, `/harness:verify`. En terminal : `harness --help`.

Installation, une fois par développeur (à la racine du repo) :
`claude plugin install harness@harness-tools --scope local`, puis
`/reload-plugins`. Nouvelle version : `harness upgrade` dans un terminal (la
session l'annonce au démarrage). Si `/harness:*` n'apparaît pas ou que `harness` est
introuvable, le plugin n'est pas installé : ne pas remplacer `harness verify`
par les seules commandes build/lint/test, et le signaler.

## Configuration

`harness.config.json` déclare les stacks et leurs commandes. La policy de
sécurité (commandes interdites, motifs de secrets, réseau) vient du moteur ;
ce fichier ne contient que les ajouts propres au projet.

## Stack

| Application | Framework | Langage | Root |
| ----------- | --------- | ------- | ---- |
| <app>       | <...>     | <...>   | `.`  |

## Commandes canoniques

- build : `<commande>`
- lint : `<commande>`
- test : `<commande>`

## Carte du système

| Je cherche…               | Je vais dans…                       |
| ------------------------- | ----------------------------------- |
| Quoi construire           | `docs/specs/`                       |
| Où j'en suis              | `docs/exec-plans/active/`           |
| Une décision passée       | `docs/decisions/`                   |
| La qualité d'un module    | `docs/quality/`                     |
| Invariants d'architecture | `docs/architecture-invariants.json` |
| Règles par langage        | `.claude/rules/`                    |
| Contrat d'API (backend)   | `docs/api/openapi.json`             |
| Contrats consommés (front) | `docs/api/backends/` (`harness tool contract-pull`) |

## Règles non négociables

1. Aucun secret dans le code (utiliser `.env`)
2. Toute feature a une spec dans `docs/specs/` AVANT le code
3. Aucune tâche n'est "terminée" sans `harness verify` vert
4. Ne jamais écrire dans `docs/decisions/` ni `.github/workflows/` sans validation
5. Ne jamais modifier `harness.config.json` sans confirmation humaine
6. Ne jamais écrire `docs/architecture-debt.json` (dette plafonnée) ni
   `docs/architecture-invariants.json` (règles désactivées) : décisions
   humaines, prises dans un terminal ou un éditeur et relues en revue

## Cycle d'une feature

- `/harness:spec` : spec en `Draft`. L'humain l'approuve en tapant
  `/harness:ship docs/specs/<nom>.md`, qui produit le plan en attente.
- L'humain approuve le plan en tapant `/harness:ship docs/specs/<nom>.md --continue`
  après relecture. Une réponse dans le chat (« ok », « validé ») n'est pas
  une approbation ; ne jamais modifier la ligne `Statut` pour contourner.
- Un plan approuvé (`ACTIVE`) est figé : pour le réviser, le repasser en
  `AWAITING_APPROVAL`, puis faire réapprouver.
- Un plan approuvé enchaîne ses étapes : il ne rend la main qu'aux points
  d'arrêt nommés dans son en-tête (`Points d'arrêt`) et sur un échec, un
  écart ou un blocage d'environnement.

## Limites d'itération

- 3 cycles max de révision de spec : une révision = une modification de la spec après sa première validation
  humaine (statut `Approved`) ; les brouillons qui précèdent cette validation
  ne comptent pas. Au-delà de 3, on s'arrête et on redécoupe le besoin.
- 2 cycles max de revue de code
- 3 échecs consécutifs d'un même test → escalade
