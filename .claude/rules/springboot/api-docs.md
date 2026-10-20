---
paths:
  - "src/main/java/com/shop/orders/*/web/**/*.java"
  - "src/main/java/com/shop/orders/**/*Controller.java"
  - "src/main/java/com/shop/orders/platform/web/**/*.java"
  - "src/main/resources/application*.yml"
  - "src/test/java/com/shop/orders/**/OpenApi*Test.java"
  - "docs/api/openapi.json"
---

# Contrat d'API — OpenAPI (Swagger), obligatoire

- Tout backend publie son contrat OpenAPI 3, généré depuis le code avec springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) : jamais écrit ni retouché à la main
- Schémas issus des `record` de requête et de réponse et de leurs contraintes Bean Validation : un schéma n'est décrit qu'une fois
- Chaque endpoint documente sa réponse de succès ET ses erreurs (`@ApiResponse`) : `400` de validation, `404` si la ressource peut manquer, `500`, au format `{ statusCode, code, message, details, traceId }`
- Chaque opération, paramètre et réponse porte sa **description** (ce que fait un paramètre, quand une réponse survient, pas son type) : `@Operation(summary = …)`, `@Parameter(description = …)`, `@ApiResponse(responseCode = …, description = …)`, `@Schema(description = …)` ; `harness gate openapi` échoue sinon
- Exposition : `springdoc.api-docs.path=/docs/openapi.json` toujours ; `springdoc.swagger-ui.path=/docs` et `springdoc.swagger-ui.enabled=${API_DOCS_UI:false}` (jamais activé en production)
- Export sans serveur ni base : test `OpenApiExportTest` (MockMvc, tranche web, adaptateurs remplacés) qui écrit `GET /docs/openapi.json` dans `build/openapi.json`
- `harness.config.json` : `"openapi": "./gradlew -q openapiExport"` (tâche qui lance ce test puis affiche `build/openapi.json`) dans `commands` de la stack
- Contrat commité dans `docs/api/openapi.json` : `harness verify` échoue si le code s'en écarte. Changement d'API voulu : `harness gate openapi --write`, puis contrat commité avec le code (le diff du contrat se relit en revue)
- Tests : chaque endpoint (`RequestMappingHandlerMapping`) figure dans le contrat ; `/docs` répond `404` sans `API_DOCS_UI=true`
