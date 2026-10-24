package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Profil {@code migrate} : seules les variables SPRING_DATASOURCE_* sont fournies, sur une base
 * vide ; Flyway crée les tables, Hibernate valide, aucun serveur web ne démarre.
 */
class MigrateProfileIT {

  @Test
  void migratesAnEmptyDatabaseWithoutWebServer() throws SQLException {
    String url = PostgresContainer.createEmptyDatabase();

    try (ConfigurableApplicationContext context =
        new SpringApplication(Application.class)
            .run(
                "--spring.config.location=classpath:/application.yml",
                "--app.migrate.exit-on-completion=false",
                "--spring.profiles.active=migrate",
                "--SPRING_DATASOURCE_URL=" + url,
                "--SPRING_DATASOURCE_USERNAME=" + PostgresContainer.username(),
                "--SPRING_DATASOURCE_PASSWORD=" + PostgresContainer.password())) {
      assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }

    try (Connection connection =
            DriverManager.getConnection(
                url, PostgresContainer.username(), PostgresContainer.password());
        var statement = connection.createStatement();
        ResultSet tables =
            statement.executeQuery(
                "SELECT count(*) FROM information_schema.tables"
                    + " WHERE table_name IN ('orders_order', 'orders_order_line')")) {
      tables.next();
      assertThat(tables.getInt(1)).isEqualTo(2);
    }
  }
}
