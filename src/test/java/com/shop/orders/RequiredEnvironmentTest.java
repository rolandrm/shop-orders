package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.platform.config.AppProperties;
import com.shop.orders.platform.config.ShopApiProperties;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Vérifie, sur le vrai {@code application.yml} (document de base), que chaque variable obligatoire
 * absente produit une erreur qui la nomme. Aucune base : le démarrage complet est prouvé dans
 * {@code RequiredEnvironmentIT}.
 */
class RequiredEnvironmentTest {

  private static final List<String> VARIABLES =
      List.of(
          "APP_SERVICE_NAME",
          "SHOP_API_BASE_URL",
          "SPRING_DATASOURCE_URL",
          "SPRING_DATASOURCE_USERNAME",
          "SPRING_DATASOURCE_PASSWORD");

  @EnableConfigurationProperties({AppProperties.class, ShopApiProperties.class})
  static class Config {}

  private static Map<String, Object> allVariables() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("APP_SERVICE_NAME", "shop-orders");
    values.put("SHOP_API_BASE_URL", "http://shop-api.test.invalid");
    values.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://db.invalid:5432/orders");
    values.put("SPRING_DATASOURCE_USERNAME", "orders");
    values.put("SPRING_DATASOURCE_PASSWORD", "not-a-real-password");
    return values;
  }

  /** Lie le document de base de application.yml ; retourne l'erreur rencontrée ou null. */
  private static Throwable bind(Map<String, Object> variables) throws IOException {
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));
    PropertySource<?> base = sources.get(0);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    new ApplicationContextRunner()
        .withUserConfiguration(Config.class)
        .withInitializer(
            ctx -> {
              ctx.getEnvironment().getPropertySources().addFirst(base);
              ctx.getEnvironment()
                  .getPropertySources()
                  .addFirst(new MapPropertySource("variables", variables));
            })
        .run(
            ctx -> {
              if (ctx.getStartupFailure() != null) {
                failure.set(ctx.getStartupFailure());
                return;
              }
              try {
                for (String key :
                    List.of(
                        "spring.datasource.url",
                        "spring.datasource.username",
                        "spring.datasource.password")) {
                  ctx.getEnvironment().getRequiredProperty(key);
                }
              } catch (RuntimeException e) {
                failure.set(e);
              }
            });
    return failure.get();
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
  void missingVariableProducesErrorNamingIt(String variable) throws IOException {
    Map<String, Object> variables = allVariables();
    variables.remove(variable);
    Throwable failure = bind(variables);
    assertThat(failure).as(variable).isNotNull();
    assertThat(failure).hasStackTraceContaining(variable);
  }

  @Test
  void allVariablesProvidedBindProperties() throws IOException {
    assertThat(VARIABLES).hasSize(5);
    assertThat(bind(allVariables())).isNull();
  }
}
