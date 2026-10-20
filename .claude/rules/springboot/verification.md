---
paths:
  - "src/test/**/ModularityTests.java"
  - "src/test/**/ArchitectureTests.java"
---

# Vérification automatique (Spring Boot)

- Verrou 1 — Spring Modulith : `ApplicationModules.of(Application.class).verify()` (internes, cycles, dépendances non déclarées)
- Verrou 2 — ArchUnit : domaine pur, injection par champ, flux standard, `java.util.logging`, bibliothèques interdites, dates legacy, entités hors infrastructure, `@Transactional` sur controller, `@Value` hors config, platform → modules
- Verrou 3 — `@NullMarked` partout + Error Prone / NullAway : violation de nullabilité = erreur de compilation
- Verrou 4 — `spotlessCheck` (google-java-format) en CI
- Invariants déclaratifs : `arch-gate.mjs` (règles `spring-*`) ; structurels : `tests/structural/springboot-modules.test.mjs`
- Projet existant : `FreezingArchRule.freeze(...)` pour geler la dette, n'échouer que sur les nouvelles violations
- Nouvelle règle INTERDIT → son verrou dans la même PR ; violation tolérée seulement avec `// TODO(archi): <tâche>`
