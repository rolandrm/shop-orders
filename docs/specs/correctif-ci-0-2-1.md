# Spec : correctif-ci-0-2-1

- **Statut** : Implemented
- **Auteur** : rolandrm
- **Version** : 0.1.0
- **Liée à** : `passer-commande.md` (adaptateur shop-api, `MigrateProcessExitIT`, décision 16 du plan), `container-image.md` (publication des images sur tag), règles `.claude/rules/springboot/` (testing, infrastructure, gradle, verification) et `.claude/rules/platform/publishing.md`
- **Révisions** : aucune

> **Choix proposés, à corriger ici avant d'approuver.**
>
> | Choix | Valeur |
> |---|---|
> | Nom de la spec | `correctif-ci-0-2-1` (le premier mot de la phrase, « Corriger », n'est pas un nom) |
> | Cause de l'échec CI | **non établie** : le journal de la CI n'est pas dans ce repo. Hypothèse de travail : le processus `migrate` de `MigrateProcessExitIT` dépasse 60 s sur le poste de la CI. La spec donne le moyen de lire la cause réelle au prochain échec, sans prétendre l'avoir trouvée |
> | Affichage des échecs | tous les tests de toutes les suites Gradle (`test`, `integrationTest`), à la fin de chaque test en échec |
> | Délai de `MigrateProcessExitIT` | 180 s ; en cas de dépassement, les 100 dernières lignes du journal du processus figurent dans le message d'échec |
> | Borne du nom de produit | 200 caractères, la valeur du contrat de shop-api v1.2.1 ; au-delà, la lecture du produit est traitée comme hors contrat (`CATALOG_UNAVAILABLE`, `503`) |
> | Plan archivé de `service-foundation` | **non modifié** : un plan archivé est un compte rendu, la mention du brouillon supprimé (lignes 50 et 53 de `docs/exec-plans/completed/service-foundation.md`) y reste |
> | Commande `build` de `harness.config.json` | `./gradlew build -x test -x integrationTest` : `check` dépend de `integrationTest` dans `build.gradle.kts`, donc `build -x test` exécute déjà les tests d'intégration que la commande `test:integration` relance ensuite. **Geste humain (règle 5)**, fait avant la fusion |
> | Livraison | la version `0.2.1` est écrite dans cette même feature (`build.gradle.kts`, `docs/api/openapi.json`) ; le tag `v0.2.1` et la preuve `release-check` sont des gestes humains après la fusion (en 0.2.0 la montée de version était une PR séparée : ici elle fait partie de la feature, une PR de moins) |

## Contexte

La CI échoue sur les tests d'intégration sans que son journal dise pourquoi : Gradle n'affiche que le nom de la tâche en échec, et `MigrateProcessExitIT` attend le processus `migrate` 60 s au plus, sans journal en cas de dépassement. Par ailleurs l'adaptateur shop-api accepte un nom de produit de plus de 200 caractères, que le contrat de shop-api v1.2.1 interdit et que la colonne `orders_order_line.product_name varchar(200)` ne peut pas stocker : la commande échouerait en `500` au lieu d'un `503` explicite. Enfin la commande `build` de `harness.config.json` exécute déjà `integrationTest` (la tâche `check` en dépend), puis `harness verify` la relance par la commande `test:integration` : les tests d'intégration, dont `MigrateProcessExitIT`, tournent deux fois par vérification. Ce correctif rend la cause des échecs lisible, borne le nom, supprime la double exécution et livre le tout en `0.2.1`.

## Périmètre

**Inclus** :

- `build.gradle.kts` : pour chaque tâche `Test` (suites `test` et `integrationTest`), affichage à la fin de chaque test en échec de son nom, de son exception, de la chaîne de ses causes et de sa pile, puis le récapitulatif des tests passés, échoués et ignorés de la tâche
- `MigrateProcessExitIT` : délai d'attente de 180 s (constante nommée) ; en cas de dépassement, le processus est tué et le message d'échec contient les 100 dernières lignes du journal du processus (`build/migrate-process-exit.log`) ; ce journal ne contient aucune valeur de variable secrète (le processus ne reçoit que les trois `SPRING_DATASOURCE_*` du conteneur de test, éphémère)
- Une classe utilitaire de test qui attend un processus avec un délai et construit ce message, avec son test
- `ShopApiProductCatalog` : un nom de plus de 200 caractères rend la réponse `200` hors contrat (`@Size(max = 200)` en plus de `@NotBlank`) ; la valeur 200 est vérifiée contre `maxLength` du contrat `docs/api/backends/shop-api.openapi.json`
- `harness.config.json`, stack `api` : la commande `build` devient `./gradlew build -x test -x integrationTest` (**geste humain, règle 5**, fait avant la fusion) ; les commandes `test` et `test:integration` ne changent pas
- Version `0.2.1` dans `build.gradle.kts` et dans `docs/api/openapi.json` (régénéré par `harness gate openapi --write`)

**Exclus** :

- Toute correction de la cause réelle de l'échec CI autre que le délai : tant que le journal n'est pas lu, elle n'est pas connue (voir Risques)
- Cause racine du thread non-démon qui retient la JVM de `migrate` (dump de threads au dépassement : hors périmètre, à décider si l'échec persiste)
- Contrôle de la devise (`EUR`, `USD`, `GBP`) dans l'adaptateur : déjà refusée plus loin par `Money.of`, hors de cette demande
- Réécriture d'un plan archivé, dont la mention du brouillon supprimé dans `docs/exec-plans/completed/service-foundation.md`
- Modification de `.github/workflows/harness.yml` (règle 4)
- Déploiement de la `0.2.1` dans un environnement : repo plateforme
- Tag `v0.2.1` et publication des images : gestes humains après la fusion

## User stories

### US-001 — La cause d'un test en échec est lisible dans la CI

**En tant que** développeur qui lit le journal de la CI **je veux** voir la cause de chaque test en échec dès qu'il échoue **afin de** corriger sans relancer la CI à l'aveugle

```gherkin
Given une suite de tests (test ou integrationTest) contenant un test qui échoue avec une exception portant une cause
When Gradle exécute la suite
Then la sortie de la tâche affiche, à la fin de ce test et avant le récapitulatif, le nom du test, le message de l'exception, la chaîne de ses causes et sa pile

Given une suite dont tous les tests passent
When Gradle exécute la suite
Then la sortie n'affiche aucune pile et le récapitulatif donne le nombre de tests passés, échoués et ignorés
```

### US-002 — Le test du processus de migration laisse un diagnostic

**En tant que** développeur **je veux** que `MigrateProcessExitIT` tolère un poste lent et montre ce que le processus a écrit **afin de** distinguer lenteur et blocage

```gherkin
Given le profil migrate lancé dans un processus séparé sur un PostgreSQL éphémère
When le processus se termine avec le code 0 en moins de 180 secondes
Then le test vérifie que les tables orders_order et orders_order_line existent

Given un processus qui ne se termine pas en 180 secondes
When le délai est dépassé
Then le processus est tué et le test échoue avec un message contenant les 100 dernières lignes du journal du processus

Given un processus qui se termine avec un code différent de 0
When le test s'exécute
Then il échoue avec le code de sortie et les 100 dernières lignes du journal du processus
```

### US-003 — Un nom de produit hors contrat est refusé à la lecture

**En tant que** client qui passe une commande **je veux** une réponse d'erreur explicite quand shop-api renvoie un nom hors contrat **afin de** ne pas subir une erreur interne à l'enregistrement

```gherkin
Given shop-api qui renvoie 200 pour un produit dont le nom compte exactement 200 caractères
When j'envoie POST /v1/orders avec ce produit
Then la commande est créée avec le nom complet

Given shop-api qui renvoie 200 pour un produit dont le nom compte 201 caractères
When j'envoie POST /v1/orders avec ce produit
Then la réponse est 503 avec le code CATALOG_UNAVAILABLE au format d'erreur commun et aucune commande n'est créée

Given shop-api qui renvoie 200 pour un produit dont le nom est vide ou ne contient que des espaces
When j'envoie POST /v1/orders avec ce produit
Then la réponse est 503 avec le code CATALOG_UNAVAILABLE (comportement inchangé)
```

### US-004 — Les tests d'intégration ne tournent qu'une fois par vérification

**En tant que** développeur qui attend la CI **je veux** que `harness verify` n'exécute les tests d'intégration qu'une fois **afin de** ne pas doubler la durée de la suite et ses échecs possibles

```gherkin
Given la commande build de harness.config.json à ./gradlew build -x test -x integrationTest
When j'exécute cette commande
Then la compilation, le formatage et les verrous d'architecture s'exécutent et la tâche integrationTest n'est pas exécutée

Given la même configuration
When harness verify s'exécute
Then la suite test et la suite integrationTest s'exécutent chacune une seule fois et la vérification est verte
```

### US-005 — La version 0.2.1 est livrable

**En tant qu'** équipe plateforme **je veux** que le service et son contrat annoncent `0.2.1` **afin que** les images du tag `v0.2.1` ne se disent pas en `0.2.0`

```gherkin
Given la version 0.2.1 dans build.gradle.kts
When harness gate openapi est lancé
Then il sort en 0 et docs/api/openapi.json annonce info.version 0.2.1

Given la fusion de la feature sur main et le tag v0.2.1 posé par l'humain
When harness tool release-check v0.2.1 est lancé
Then le tag est sur main et les images ghcr.io/rolandrm/shop-orders:0.2.1 et ghcr.io/rolandrm/shop-orders-migrate:0.2.1 sont au registre
```

## Contrat d'API

Aucun endpoint n'est ajouté ni modifié, aucun schéma de requête ou de réponse ne change. Le contrat commité `docs/api/openapi.json` ne change que par `info.version` (`0.2.0` vers `0.2.1`), régénéré par `harness gate openapi --write`.

Un seul comportement observable change, sur `POST /v1/orders` : si shop-api renvoie un nom de produit de plus de 200 caractères, la réponse est `503` `CATALOG_UNAVAILABLE` au format commun `{ "statusCode", "code", "message", "details", "traceId" }` (cas déjà documenté dans le contrat pour une réponse de shop-api hors contrat), au lieu d'une erreur interne à l'enregistrement. Appel sortant vers shop-api : inchangé (`GET {SHOP_API_BASE_URL}/v1/products/{id}`, contrat v1.2.1, `name` : 1 à 200 caractères).

## Contraintes non fonctionnelles

- Sécurité : aucun secret dans les journaux affichés ; le journal du processus `migrate` ne reçoit que les trois `SPRING_DATASOURCE_*` d'un conteneur éphémère ; le message d'échec d'un test n'ajoute aucune variable d'environnement
- Taille des journaux : au plus 100 lignes du journal du processus par échec ; la pile complète n'est affichée que pour les tests en échec
- Durée : le délai de 180 s ne s'applique qu'à un échec ; un `migrate` sain reste de l'ordre de 10 à 20 s
- Coût : aucune dépendance ajoutée

## Plan de test

| Id | Niveau | Scénario | Attendu | Couvre |
|----|--------|----------|---------|--------|
| T1 | outillage (sonde) | Un test temporaire échouant avec une cause imbriquée est ajouté à la suite `test`, `./gradlew test --tests <sonde>` est lancé, puis le fichier est retiré | la sortie contient le nom du test, le message, la cause et la pile, avant `BUILD FAILED` ; la sonde n'est pas dans l'arbre de travail à la fin | US-001 |
| T2 | outillage (sonde) | Même sonde dans la suite `integrationTest` | même contenu pour `./gradlew integrationTest --tests <sonde>` | US-001 |
| T3 | outillage | `./gradlew test` sur la suite actuelle, tous tests verts | aucune pile affichée ; récapitulatif des tests passés, échoués et ignorés | US-001 |
| T4 | unit (utilitaire d'attente de processus) | un processus qui écrit 150 lignes puis dort, délai de 2 s | échec d'assertion dont le message contient les lignes 51 à 150 et pas la ligne 1 ; le processus n'est plus vivant | US-002 |
| T5 | unit (utilitaire) | un processus qui écrit une ligne et sort avec le code 3 | échec dont le message contient le code 3 et la ligne écrite | US-002 |
| T6 | unit (utilitaire) | un processus qui sort avec le code 0 avant le délai | aucun échec, le code 0 est rendu | US-002 |
| T7 | intégration (Testcontainers) | `MigrateProcessExitIT` avec le délai de 180 s (la constante vaut 180 s, vérifiée par une assertion) | code de sortie 0 et les deux tables existent | US-002 |
| T8 | intégration (`MockRestServiceServer`) | `ShopApiProductCatalog` lit un produit dont le nom a 200 caractères, puis 201 caractères, puis 1 caractère, puis est vide, puis blanc | succès avec le nom entier pour 200 et pour 1 ; `CATALOG_UNAVAILABLE` pour 201, vide et blanc | US-003 |
| T9 | intégration | `ShopApiContractConformanceIT` lit `maxLength` de `name` dans `docs/api/backends/shop-api.openapi.json` | il vaut 200 et la borne de l'adaptateur est égale à cette valeur | US-003 |
| T10 | intégration (`@WebMvcTest`) | `POST /v1/orders` avec un catalogue faux qui rend `CATALOG_UNAVAILABLE` | `503` au format commun, aucune commande enregistrée (cas existant, non régressif) | US-003 |
| T11 | outillage | `harness.config.json` déclare `build` à `./gradlew build -x test -x integrationTest` ; `./gradlew build -x test -x integrationTest --dry-run` | la commande déclarée est celle-ci ; la liste des tâches ne contient pas `:integrationTest` ni `:test`, et contient la compilation (`:compileJava`) et `:spotlessCheck` | US-004 |
| T12 | outillage | `harness gate openapi` après `harness gate openapi --write`, et `OpenApiExportTest` | sort en 0 ; `info.version` vaut `0.2.1` et égale `project.version` | US-005 |
| T13 | structure | `harness gate arch --rule spring-no-standard-streams`, `--rule spring-no-cross-module-internals`, `--rule spring-tests-stack`, `--rule spring-no-static-state`, `--rule spring-value-only-in-config` | chacune sort en 0 | US-002, US-003 |
| T15 | outillage | `harness verify` après le changement de commande | vert ; le rapport liste `api:test` et `api:test:integration` chacun une fois | US-004 |
| T14 | humain | après fusion et pose du tag `v0.2.1` : `harness tool release-check v0.2.1` | « ✅ Tous les contrôles passent » : tag sur main, images du service et `-migrate` au registre à la version `0.2.1` | US-005 |

Le test T14 se fait à un point d'arrêt du plan : l'humain lance la commande dans un terminal, l'agent lit le rapport enregistré dans `.claude/state/proofs/`, rien à coller. Dépendances que le plan de test suppose : aucune de nouvelle (JUnit 5, AssertJ, `MockRestServiceServer` et Testcontainers sont déjà déclarés).

## Risques

- **La cause réelle de l'échec CI n'est pas établie.** Si `MigrateProcessExitIT` n'est pas en cause (Docker indisponible sur le poste de la CI, port occupé, mémoire), le délai de 180 s ne corrige rien ; l'affichage des causes (US-001) la montrera au premier échec suivant, et un correctif ultérieur sera une autre spec. Le défaut possible : `harness verify` en CI peut filtrer la sortie de Gradle ; si la cause reste invisible, il faudra lire le rapport XML de `build/test-results`
- Un délai de 180 s masque une lenteur anormale du `migrate` : le journal en cas de dépassement et l'arrêt en 10 à 20 s observé en local servent de repère
- Un nom de produit de plus de 200 caractères rendu `503` bloque la commande d'un produit qui existe chez shop-api ; c'est cohérent avec le contrat (qui l'interdit) et préférable à une erreur `500` à l'enregistrement
- `0.2.1` publiée trop tôt : un tag d'image publié ne change jamais (`publishing.md`), donc la correction d'une erreur de version serait une `0.2.2`
- La commande `build` doit être changée avant la fusion : sans ce geste humain, la CI exécute encore les tests d'intégration deux fois

## Déploiement et retour arrière

- **Ordre de mise en place** : (1) **geste humain (règle 5), avant la CI** : changer `build` dans `harness.config.json` en `./gradlew build -x test -x integrationTest` ; (2) fusion de la feature sur main, CI verte ; (3) l'humain pose le tag `v0.2.1` sur main ; (4) la CI publie `ghcr.io/rolandrm/shop-orders:0.2.1` et `ghcr.io/rolandrm/shop-orders-migrate:0.2.1` ; (5) `harness tool release-check v0.2.1` ; aucune migration de base, aucune variable d'environnement nouvelle
- **Compatibilité** : schéma de base inchangé (`V1` seule migration), configuration inchangée ; la `0.2.0` tourne sur ce que la `0.2.1` laisse en base
- **Retour arrière** : revenir aux images `0.2.0` (tags immuables) ; rien n'est perdu, aucune donnée n'est écrite différemment ; la preuve de retour arrière se fait dans shop-platform, à la PR de version qui adopte la `0.2.1` (N-1 = `0.2.0`, avec base)
- **Vérification après déploiement** : `GET /actuator/health` répond `200` ; `GET /docs/openapi.json` annonce `info.version` `0.2.1` ; `POST /v1/orders` avec un produit réel de shop-api répond `201`
