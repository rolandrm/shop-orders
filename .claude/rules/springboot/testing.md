---
paths:
  - "src/test/**/*.java"
---

# Tests

- JUnit 5 + AssertJ (jamais JUnit 4) ; Testcontainers PostgreSQL (`@ServiceConnection`), jamais H2
- Unitaire : domaine + cas d'usage, SANS Spring ni BDD, fakes en mémoire
- Module : `@ApplicationModuleTest` (API `Scenario` pour les événements)
- Persistance : `@DataJpaTest` + Testcontainers ; Web : `@WebMvcTest` + `MockMvcTester` ; E2E : `@SpringBootTest(RANDOM_PORT)`, peu nombreux
- Fakes dans `src/test/java/…/fakes/`, jamais dans `src/main`
- Un port qui a un fake et un adaptateur réel : une seule suite de contrat (classe de test abstraite, une sous-classe par implémentation), exécutée contre le fake en unitaire et contre l'adaptateur en intégration ; jamais deux suites qui divergent
- Mockito avec parcimonie, jamais pour mocker le domaine
- Suites Gradle séparées `test` / `integrationTest` ; conteneur PostgreSQL partagé par suite
- `ModularityTests` (`ApplicationModules.verify()`) et `ArchitectureTests` (ArchUnit) exécutés à chaque build
