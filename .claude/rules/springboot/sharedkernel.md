---
paths:
  - "src/main/java/com/shop/orders/sharedkernel/**/*.java"
---

# sharedkernel — contrats transverses (module OPEN)

- Java PUR : JDK + annotations JSpecify uniquement ; aucun Spring, JPA, Hibernate, Bean Validation, Jackson
- Contenu : `Result<T>` (sealed : `Success` / `Failure`), `DomainError` (`kind` + `code` stable), `DomainEvent`, ports transverses (`port/` : `IdGenerator`, `EventPublisher`, `TransactionRunner`), value objects réellement transverses (`Money`)
- Un concept utilisé par un seul module appartient à ce module
- N'importe RIEN du projet (ni `platform`, ni un module métier)
- `package-info.java` : `@ApplicationModule(type = Type.OPEN)` + `@NullMarked`
