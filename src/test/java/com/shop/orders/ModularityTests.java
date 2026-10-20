// Template Spring Modulith — harness Spring Boot (verrou 1)
// À copier dans src/test/java/com/shop/orders/ModularityTests.java
package com.shop.orders;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  static final ApplicationModules modules = ApplicationModules.of(Application.class);

  @Test
  void verifies_module_boundaries() {
    modules.verify(); // accès aux internes, cycles, dépendances non déclarées
  }

  @Test
  void writes_documentation() {
    new Documenter(modules).writeDocumentation(); // diagrammes générés dans build/
  }
}
