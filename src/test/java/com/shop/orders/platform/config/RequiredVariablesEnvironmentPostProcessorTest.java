package com.shop.orders.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/** Sans base ni contexte : environnement simulé et appel direct du post-processeur. */
class RequiredVariablesEnvironmentPostProcessorTest {

  private static final String WITNESS = "valeur-temoin-123";

  private final RequiredVariablesEnvironmentPostProcessor processor =
      new RequiredVariablesEnvironmentPostProcessor();

  private static Map<String, String> all() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("APP_SERVICE_NAME", WITNESS + "-a");
    values.put("SHOP_API_BASE_URL", WITNESS + "-b");
    values.put("SPRING_DATASOURCE_URL", WITNESS + "-c");
    values.put("SPRING_DATASOURCE_USERNAME", WITNESS + "-d");
    values.put("SPRING_DATASOURCE_PASSWORD", WITNESS + "-e");
    return values;
  }

  private void run(Map<String, String> values, boolean migrate) {
    MockEnvironment environment = new MockEnvironment();
    values.forEach(environment::setProperty);
    if (migrate) {
      environment.setActiveProfiles("migrate");
    }
    processor.postProcessEnvironment(environment, new SpringApplication());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "APP_SERVICE_NAME",
        "SHOP_API_BASE_URL",
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD"
      })
  void absentVariableIsNamedWithoutProfile(String variable) {
    Map<String, String> values = all();
    values.remove(variable);

    assertThatThrownBy(() -> run(values, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(variable);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "APP_SERVICE_NAME",
        "SHOP_API_BASE_URL",
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD"
      })
  void emptyVariableIsNamedWithoutProfile(String variable) {
    Map<String, String> values = all();
    values.put(variable, "  ");

    assertThatThrownBy(() -> run(values, false)).hasMessageContaining(variable);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD"
      })
  void absentDatasourceVariableIsNamedWithMigrateProfile(String variable) {
    Map<String, String> values = all();
    values.remove(variable);

    assertThatThrownBy(() -> run(values, true)).hasMessageContaining(variable);
  }

  @Test
  void migrateProfileDoesNotRequireServiceNameNorShopApiUrl() {
    Map<String, String> values = all();
    values.remove("APP_SERVICE_NAME");
    values.remove("SHOP_API_BASE_URL");

    assertThatCode(() -> run(values, true)).doesNotThrowAnyException();
  }

  @Test
  void allVariablesPresentPass() {
    assertThatCode(() -> run(all(), false)).doesNotThrowAnyException();
    assertThatCode(() -> run(all(), true)).doesNotThrowAnyException();
  }

  @Test
  void messageNeverContainsProvidedValues() {
    Map<String, String> values = all();
    values.remove("SPRING_DATASOURCE_URL");

    assertThatThrownBy(() -> run(values, false))
        .satisfies(error -> assertThat(error.getMessage()).doesNotContain(WITNESS));
  }
}
