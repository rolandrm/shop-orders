---
paths:
  - "src/main/resources/**"
---

# Configuration et migrations

- `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate` (jamais `update` / `create`)
- `hibernate.jdbc.time_zone=UTC`, `hibernate.jdbc.batch_size` configuré, `order_inserts` / `order_updates` activés
- Jackson strict : `fail-on-unknown-properties: true`, `fail-on-null-for-primitives: true`, `write-dates-as-timestamps: false`
- `server.error.include-stacktrace=never`, `server.error.include-message=never`
- `spring.threads.virtual.enabled=true`
- Actuator : probes liveness / readiness ; seuls `health` et `info` exposés publiquement
- Profils `local`, `test`, `prod` ; `application.yml` ne contient AUCUN secret
- Flyway : `db/migration/V{n}__{description}.sql` ; une migration appliquée n'est jamais modifiée ; destructive en deux déploiements
- En `prod` : `spring.flyway.enabled=false`, migrations appliquées par une étape de déploiement dédiée
