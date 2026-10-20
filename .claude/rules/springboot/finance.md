---
paths:
  - "src/main/java/com/shop/orders/**/*.java"
---

# [OPTIONNEL] Flux financiers

- Jamais `double` / `float` pour un montant : `BigDecimal` encapsulé dans `Money` (`sharedkernel`)
- `new BigDecimal(double)` interdit : toujours depuis une chaîne
- `Money` = montant + `java.util.Currency` ; deux devises différentes = erreur de domaine ; décimales par devise
- Comparaisons par `compareTo`, jamais `equals`
- Arrondi explicite, uniquement dans `Money` (`RoundingMode.UNNECESSARY` par défaut)
- Stockage `NUMERIC(p, s)` via `@Column(precision, scale)` ; API : montant en chaîne validée par `@Pattern`
- `Idempotency-Key` obligatoire sur toute opération qui déplace de l'argent
- Grand livre en partie double, écritures immuables ; corrections par contre-passation, jamais `UPDATE` / `DELETE`
- Verrou sur les comptes débités + test de concurrence par opération de débit
