package com.shop.orders;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Conteneur PostgreSQL partagé par toute la suite {@code integrationTest} : démarré une fois,
 * jamais arrêté par un contexte Spring (Ryuk le retire à la fin du processus).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainer {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.11-alpine");

  static {
    POSTGRES.start();
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return POSTGRES;
  }

  /** Crée une base vide et unique sur le conteneur ; retourne son URL JDBC. */
  public static String createEmptyDatabase() {
    String name = "db_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    } catch (SQLException e) {
      throw new IllegalStateException("Création de la base de test impossible", e);
    }
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + name;
  }

  public static String username() {
    return POSTGRES.getUsername();
  }

  public static String password() {
    return POSTGRES.getPassword();
  }
}
