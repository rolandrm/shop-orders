---
paths:
  - "src/main/java/com/shop/orders/**/*.java"
---

# Frontières entre modules (Spring Modulith)

- Chaque package de premier niveau sous `com.shop.orders` est un module applicatif
- API d'un module = types publics de son package RACINE ; tout sous-package (`domain`, `application`, `infrastructure`, `web`, `wiring`, `internal`) est interne
- Un module n'importe JAMAIS un sous-package d'un autre module (entité, repository, service) ; `sharedkernel` et `platform` sont OPEN
- Chaque module a un `package-info.java` : `@ApplicationModule(allowedDependencies = {…})` explicite + `@NullMarked`
- Deux canaux : façade synchrone (interface publique, types de lecture dédiés) ou événements publics (`record` versionné)
- Réaction inter-modules : `@ApplicationModuleListener` (async, après commit), jamais `@EventListener` synchrone
- Aucun cycle entre modules : événement ou extraction d'un concept commun, jamais `@Lazy`
- Aucune relation JPA ni jointure JPQL entre entités de deux modules : référence par identifiant
- Chaque table appartient à un seul module
