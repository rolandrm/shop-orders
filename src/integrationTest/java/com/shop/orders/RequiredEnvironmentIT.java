package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.orders.platform.config.RequiredVariablesEnvironmentPostProcessor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Démarrage complet sur le vrai {@code application.yml} (seul lu) et un PostgreSQL réel : les 5
 * variables obligatoires, le schéma migré, et le refus de démarrer sur un schéma vide.
 */
class RequiredEnvironmentIT {

  private static Map<String, String> variables(String url) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("APP_SERVICE_NAME", "shop-orders");
    values.put("SHOP_API_BASE_URL", "http://shop-api.test.invalid");
    values.put("SPRING_DATASOURCE_URL", url);
    values.put("SPRING_DATASOURCE_USERNAME", PostgresContainer.username());
    values.put("SPRING_DATASOURCE_PASSWORD", PostgresContainer.password());
    return values;
  }

  private static void migrate(String url) {
    Flyway.configure()
        .dataSource(url, PostgresContainer.username(), PostgresContainer.password())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  private static ConfigurableApplicationContext start(
      Map<String, String> variables, String... extraArgs) {
    List<String> args = new ArrayList<>(List.of(extraArgs));
    args.add("--spring.config.location=classpath:/application.yml");
    args.add("--app.migrate.exit-on-completion=false");
    args.add("--spring.main.web-application-type=none");
    variables.forEach((name, value) -> args.add("--" + name + "=" + value));
    return new SpringApplication(Application.class).run(args.toArray(String[]::new));
  }

  @Test
  void startsWhenAllVariablesAreProvidedAndSchemaIsMigrated() {
    String url = PostgresContainer.createEmptyDatabase();
    migrate(url);

    try (ConfigurableApplicationContext context = start(variables(url))) {
      assertThat(context.isRunning()).isTrue();
    }
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
  void startupFailsAndNamesTheMissingVariable(String variable) {
    String url = PostgresContainer.createEmptyDatabase();
    migrate(url);
    Map<String, String> values = variables(url);
    values.remove(variable);

    assertThatThrownBy(() -> start(values).close()).hasStackTraceContaining(variable);
  }

  @Test
  void migrateProfileStartsWithOnlyDatasourceVariables() {
    String url = PostgresContainer.createEmptyDatabase();
    Map<String, String> values = variables(url);
    values.remove("APP_SERVICE_NAME");
    values.remove("SHOP_API_BASE_URL");

    try (ConfigurableApplicationContext context =
        start(values, "--spring.profiles.active=migrate")) {
      assertThat(context.isRunning()).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD"
      })
  void migrateProfileFailsAndNamesTheMissingDatasourceVariable(String variable) {
    String url = PostgresContainer.createEmptyDatabase();
    Map<String, String> values = variables(url);
    values.remove("APP_SERVICE_NAME");
    values.remove("SHOP_API_BASE_URL");
    values.remove(variable);

    assertThatThrownBy(() -> start(values, "--spring.profiles.active=migrate").close())
        .hasStackTraceContaining(RequiredVariablesEnvironmentPostProcessor.MESSAGE_PREFIX)
        .hasStackTraceContaining(variable);
  }

  @Test
  void startupFailsOnEmptySchemaWithoutFlywayAndCreatesNoTable() throws SQLException {
    String url = PostgresContainer.createEmptyDatabase();

    assertThatThrownBy(() -> start(variables(url)).close()).hasStackTraceContaining("orders_order");

    try (Connection connection =
            DriverManager.getConnection(
                url, PostgresContainer.username(), PostgresContainer.password());
        var statement = connection.createStatement();
        ResultSet tables =
            statement.executeQuery(
                "SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 'orders\\_%'")) {
      tables.next();
      assertThat(tables.getInt(1)).isZero();
    }
  }
}
