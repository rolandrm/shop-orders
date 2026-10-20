---
paths:
  - "src/main/java/com/shop/orders/*/infrastructure/**/*.java"
---

# Infrastructure — adaptateurs JPA (module riche)

- Entité JPA DISTINCTE de l'objet de domaine, package-private, ne sort jamais du package
- Entité : constructeur `protected` sans argument, champs privés, aucune annotation Jackson, `@Version`
- `@Table(name = "<module>_<table>")` ; colonnes en `snake_case`
- `@ManyToOne` / `@OneToOne` toujours `fetch = FetchType.LAZY` ; relations seulement à l'intérieur d'un agrégat
- Repository Spring Data = interface package-private ; adaptateur du port `@Repository` package-private
- Mapper entité ↔ domaine écrit à la main dans le package
- Chargements explicites (`@EntityGraph`, `JOIN FETCH`) ; aucune boucle qui déclenche du lazy loading
- Lectures de listes par projection `record`
- `equals` / `hashCode` basés sur l'identifiant
- `@Lock(PESSIMISTIC_WRITE)` sur une méthode dédiée ; SQL natif via `@Query(nativeQuery = true)` / `JdbcClient` avec paramètres liés, jamais de concaténation
- Messages externes entrants : désérialisés dans un `record` et validés (`Validator`) avant traitement
