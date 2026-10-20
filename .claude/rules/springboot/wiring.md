---
paths:
  - "src/main/java/com/shop/orders/*/wiring/**/*.java"
  - "src/main/java/com/shop/orders/*/package-info.java"
  - "src/main/java/com/shop/orders/Application.java"
---

# Câblage et déclaration des modules

- `wiring/XxxWiring.java` : `@Configuration(proxyBeanMethods = false)`, package-private, déclare les cas d'usage et la façade par `@Bean`
- Seul endroit du module qui assemble ; les adaptateurs sont détectés par scan (`@Repository`)
- `package-info.java` : `@ApplicationModule(allowedDependencies = {…})` + `@NullMarked` ; `sharedkernel` et `platform` en `Type.OPEN`
- `Application.java` : `@SpringBootApplication` + `@Modulithic(sharedModules = {"sharedkernel", "platform"})`
