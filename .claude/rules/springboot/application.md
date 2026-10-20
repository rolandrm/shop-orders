---
paths:
  - "src/main/java/com/shop/orders/*/application/**/*.java"
---

# Application — cas d'usage (module riche)

- Java PUR : aucune annotation Spring (`@Service`, `@Transactional`…) ; câblé dans `wiring/`
- Une classe `final` par intention (`PayOrder`, `CancelOrder`) ; pas de `OrderService` fourre-tout
- Dépendances par constructeur (ports, `TransactionRunner`, `EventPublisher`, `Clock`)
- Retour `Result<T>` pour les échecs attendus ; `TransactionRunner.inTransaction` (rollback si `Failure`)
- Aucun appel HTTP sortant ni envoi de message dans une transaction
- Commande = `record` simple ; jamais un DTO web annoté
- Autorisation sur la ressource via le port `CurrentUser`, pas seulement l'authentification
- Imports autorisés : son `domain`, `sharedkernel`, API (package racine) des autres modules
