---
paths:
  - "src/main/java/com/shop/orders/*/domain/**/*.java"
---

# Domaine (module riche)

- Java PUR : aucun import Spring, `jakarta.persistence`, `jakarta.validation`, Hibernate, Jackson
- Aucune annotation autre que JSpecify (`@Nullable`) et celles du JDK (`@Override`)
- Agrégat : constructeur privé, création par méthode statique qui valide les invariants et retourne `Result`
- `restore(...)` pour la reconstitution depuis la persistance, sans revalidation
- Aucun setter public ; état modifié par des méthodes métier nommées
- Identifiants typés (`record OrderId(UUID value)`), jamais de `UUID` nu dans les signatures
- Événements enregistrés par l'agrégat (`pullEvents()`), publiés par le cas d'usage
- Erreurs = `record … implements DomainError` avec `code` stable en `SCREAMING_SNAKE_CASE`
- Ports du module = interfaces Java (ex. `OrderRepository`)
- Jamais `Instant.now()`, `UUID.randomUUID()`, `LocalDateTime.now()` : `Clock` et `IdGenerator` injectés
- Un test JUnit sans Spring par règle métier
