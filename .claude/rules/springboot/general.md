---
paths:
  - "src/main/java/com/shop/orders/**/*.java"
---

# Règles Spring Boot transverses

- Une seule bibliothèque par sujet (stack standard) : Spring MVC + threads virtuels, Spring Data JPA, Flyway, Bean Validation, Jackson, `RestClient`, Resilience4j, ShedLock, SLF4J
- Interdit hors écart justifié : Lombok, MapStruct, ModelMapper, Dozer, Joda-Time, Guava (ce que fait le JDK), OkHttp, Feign, `WebClient` / WebFlux, Liquibase, `java.util.logging`, Log4j
- `record` pour DTOs, commandes, value objects, événements ; classes explicites ailleurs
- Injection par constructeur uniquement (constructeur unique, sans annotation) ; jamais `@Autowired` / `@Inject`
- Aucun `System.out` / `System.err` / `printStackTrace()` : logger SLF4J
- Aucun `@Value` hors `platform.config` : `@ConfigurationProperties` validées
- Aucune classe `static` à état : pas de singleton manuel, pas de cache statique
- `java.time` uniquement (`Instant` en interne) ; jamais `java.util.Date`, `Calendar`, `java.sql.Timestamp`
- Aucune donnée sensible dans les logs (tokens, mots de passe, cartes, identité, santé)
- Ces règles décrivent l'ÉTAT RÉEL ; une décision non implémentée est marquée 🎯 et listée dans les chantiers ouverts
- Toute règle INTERDIT détectable mécaniquement a son verrou (Spring Modulith, ArchUnit, NullAway, arch-gate)
