// Template ArchUnit — harness Spring Boot (verrou 2)
// À copier dans src/test/java/com/shop/orders/ArchitectureTests.java
// puis remplacer com.shop.orders. Sur un projet existant : envelopper les règles avec
// FreezingArchRule.freeze(...) pour geler la dette. Vérifier les noms des constantes
// GeneralCodingRules selon la version d'ArchUnit installée.
package com.shop.orders;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.shop.orders", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

  @ArchTest
  static final ArchRule domain_is_pure =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..", "..sharedkernel..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "jakarta.validation..",
              "org.hibernate..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "lombok..");

  @ArchTest
  static final ArchRule sharedkernel_is_standalone =
      noClasses()
          .that()
          .resideInAPackage("com.shop.orders.sharedkernel..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("com.shop.orders..")
                  .and(not(resideInAPackage("com.shop.orders.sharedkernel.."))));

  // Générique : aucune liste de modules à maintenir
  @ArchTest
  static final ArchRule platform_does_not_know_modules =
      noClasses()
          .that()
          .resideInAPackage("com.shop.orders.platform..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("com.shop.orders..")
                  .and(
                      not(
                          resideInAnyPackage(
                              "com.shop.orders.platform..", "com.shop.orders.sharedkernel.."))));

  @ArchTest
  static final ArchRule no_field_injection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  @ArchTest
  static final ArchRule no_standard_streams =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule no_java_util_logging =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest
  static final ArchRule no_forbidden_libraries =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "lombok..",
              "org.mapstruct..",
              "org.modelmapper..",
              "org.joda.time..",
              "okhttp3..",
              "feign..",
              "org.springframework.web.reactive..",
              "reactor.core..");

  @ArchTest
  static final ArchRule no_legacy_dates =
      noClasses()
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(java.util.Date.class, java.util.Calendar.class, java.sql.Timestamp.class);

  @ArchTest
  static final ArchRule entities_stay_in_adapters =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .resideInAnyPackage("..infrastructure..", "..internal..");

  @ArchTest
  static final ArchRule web_never_touches_entities =
      noClasses()
          .that()
          .resideInAPackage("..web..")
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(Entity.class);

  @ArchTest
  static final ArchRule spring_data_repositories_are_package_private =
      classes()
          .that()
          .areInterfaces()
          .and()
          .areAssignableTo(Repository.class)
          .should()
          .notBePublic();

  @ArchTest
  static final ArchRule no_transactional_controller_classes =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(Transactional.class);

  @ArchTest
  static final ArchRule no_transactional_controller_methods =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .should()
          .beAnnotatedWith(Transactional.class);

  @ArchTest
  static final ArchRule value_only_in_config =
      noFields()
          .that()
          .areDeclaredInClassesThat()
          .resideOutsideOfPackage("..platform.config..")
          .should()
          .beAnnotatedWith(Value.class);
}
