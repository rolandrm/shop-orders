---
paths:
  - "build.gradle.kts"
  - "settings.gradle.kts"
  - "gradle/libs.versions.toml"
---

# Gradle (Kotlin DSL)

- Versions UNIQUEMENT dans `gradle/libs.versions.toml` ; aucune coordonnée `groupe:artefact:version` en dur
- Wrapper commité ; toolchain Java LTS ; `.java-version` pour la CI
- `dependencyLocking { lockAllConfigurations() }` ; `./gradlew dependencies --write-locks` après toute modification ; lockfile commité
- Plugins : Spring Boot, dependency-management, Spotless (google-java-format), Error Prone + NullAway
- Aucune dépendance hors stack : Lombok, MapStruct, ModelMapper, Liquibase, H2, OkHttp, Feign, Joda-Time
- `./gradlew build` = compilation + formatage + tests + verrous d'architecture ; `integrationTest` séparé
