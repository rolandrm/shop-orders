---
paths:
  - "src/main/java/com/shop/orders/*/web/**/*.java"
  - "src/main/java/com/shop/orders/**/*Controller.java"
---

# Web — controllers

- Controller fin : validation → UN cas d'usage / service → `record` de réponse
- Aucun `@Transactional`, repository, `EntityManager`, `JdbcClient` ni `TransactionRunner` dans un controller
- Aucune entité JPA ni objet de domaine exposé : un `record` de réponse par endpoint
- Requête : `record` avec `@Valid @RequestBody`, bornes explicites (`@Size`, `@Max`, `@Positive`) ; paramètres validés avec `@Validated`
- DTO converti en commande applicative dans le controller
- `Results.unwrap(result)` ; erreurs mappées par le `@RestControllerAdvice` de `platform.web`
- Format d'erreur : `{ statusCode, code, message, details, traceId }` (pas `ProblemDetail`)
- Conventions API : `camelCase`, `/v1/…`, ressources au pluriel en `kebab-case`, UUIDv7, `Instant` ISO 8601 UTC, pagination par curseur (`limit` ≤ 100), `Idempotency-Key`, `X-Request-Id`
- `@PreAuthorize` autorisé pour les contrôles de rôle simples
- WebSocket / SSE : aucune logique métier, auth au handshake
