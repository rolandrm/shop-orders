---
paths:
  - "src/main/java/com/shop/orders/platform/**/*.java"
---

# platform — infrastructure transverse (module OPEN)

- N'importe JAMAIS un module métier
- `config/` : `record` `@ConfigurationProperties` + `@Validated` par domaine ; seul endroit autorisé pour `@Value` ; garde au démarrage si `prod` + profil de dev
- `persistence/` : `TransactionRunner` (`TransactionTemplate`, rollback si `Failure`), `IdGenerator` UUIDv7, bean `Clock.systemUTC()`
- `messaging/` : `EventPublisher` → `ApplicationEventPublisher` (registre de publication Spring Modulith)
- `web/` : `@RestControllerAdvice` unique (`DomainError.Kind` → HTTP), `Results.unwrap`, corrélation `X-Request-Id` (MDC), pagination
- `security/` : `SecurityFilterChain` stateless, `anyRequest().authenticated()`, routes publiques listées ici seulement, JWT resource server, Bucket4j, `Argon2PasswordEncoder`, CORS explicite
- `httpclient/` : `RestClient` avec timeouts explicites + Resilience4j borné
- `observability/` : logs JSON structurés (`traceId`, `spanId`, `requestId`), Micrometer → OTLP, port `BusinessMetrics`
- Tâches planifiées : `@Scheduled` + `@SchedulerLock` (ShedLock), idempotentes, appellent un cas d'usage
