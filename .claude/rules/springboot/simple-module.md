---
paths:
  - "src/main/java/com/shop/orders/*/internal/**/*.java"
---

# Module simple (couches Spring)

- Un seul sous-package `internal/` : controller, service, entité, repository Spring Data, DTOs
- L'entité JPA est le modèle ; service `@Service` package-private avec `@Transactional` (`readOnly` en lecture)
- Reste obligatoire : `package-info.java`, API limitée au package racine, `record` de réponse, erreurs via `DomainError`, règles JPA
- `@Transactional` sur une méthode `private` ou appelée depuis la même classe n'a aucun effet
- Signal de bascule vers Riche : `if` sur des statuts, cohérence entre champs, règle testable seulement avec une BDD
