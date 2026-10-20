# Spec : {nom}

- **Statut** : Draft | Review | Approved | Implemented
- **Auteur** : {nom}
- **Version** : 0.0.0
- **Liée à** : {spec parente ou ADR}

## Contexte

{Problème résolu, 3 phrases maximum}

## Périmètre

**Inclus** : {liste}
**Exclus** : {liste}

## User stories

### US-001 — {titre}

**En tant que** {acteur} **je veux** {action} **afin de** {bénéfice}

```gherkin
Given {contexte}
When {action}
Then {résultat attendu}
```

## Contrat d'API

{Schémas de requête et de réponse, codes d'erreur ; « Sans objet » si la feature n'expose rien.
Backend : ces schémas entrent dans le contrat OpenAPI commité `docs/api/openapi.json`}

## Contraintes non fonctionnelles

{Latence, sécurité, coût : chiffrées}

## Plan de test

| Id | Niveau | Scénario | Attendu | Couvre |
|----|--------|----------|---------|--------|
| T1 | {unit / intégration / e2e / structure} | {scénario} | {résultat observable} | {US-001} |

Chaque user story est couverte par au moins un test (colonne « Couvre ») :
`harness gate spec` refuse sinon une spec en rédaction.

Un contrôle de structure s'appuie sur un invariant du harness quand il en
existe un (`harness gate arch --rule <id>`), pas sur une recherche de texte.

## Risques

{Ce qui peut casser, et comment on le verra}

## Déploiement et retour arrière

- **Ordre de mise en place** : {migrations, variables d'environnement, services externes, dans l'ordre}
- **Compatibilité** : {l'ancienne version de l'application tourne-t-elle sur le nouveau schéma ou la nouvelle configuration ?}
- **Retour arrière** : {comment revenir à la version précédente, et ce qui est perdu (données écrites entre-temps)}
- **Vérification après déploiement** : {requête ou signal qui prouve que ça marche}

Écrire « Sans objet : {raison} » pour une ligne qui ne s'applique pas.
