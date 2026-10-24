package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Profils {@code migrate,seed} sur le schéma contraint de V1 : sans violation, idempotent. */
class SeedIT {

  @Test
  void seedsTwiceWithSameRowsThenMigrateAloneStarts() throws SQLException {
    String url = PostgresContainer.createEmptyDatabase();

    run(url, "migrate,seed");
    long orders = count(url, "orders_order");
    long lines = count(url, "orders_order_line");
    assertThat(orders).isGreaterThan(0);
    assertThat(lines).isGreaterThan(0);

    run(url, "migrate,seed");
    assertThat(count(url, "orders_order")).isEqualTo(orders);
    assertThat(count(url, "orders_order_line")).isEqualTo(lines);

    run(url, "migrate");
    assertThat(count(url, "orders_order")).isEqualTo(orders);
  }

  @Test
  void seededLinesHonourLineTotalAndOrderTotals() throws SQLException {
    String url = PostgresContainer.createEmptyDatabase();
    run(url, "migrate,seed");

    try (Connection connection = connect(url);
        var statement = connection.createStatement()) {
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT count(*), count(*) FILTER (WHERE line_total_amount = unit_price_amount *"
                  + " quantity) FROM orders_order_line")) {
        rs.next();
        assertThat(rs.getLong(1)).isGreaterThan(0);
        assertThat(rs.getLong(2)).isEqualTo(rs.getLong(1));
      }
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT count(*) FROM orders_order o WHERE o.total_amount <> (SELECT"
                  + " sum(l.line_total_amount) FROM orders_order_line l WHERE l.order_id = o.id)"
                  + " OR o.item_count <> (SELECT count(*) FROM orders_order_line l WHERE"
                  + " l.order_id = o.id)")) {
        rs.next();
        assertThat(rs.getLong(1)).isZero();
      }
    }
  }

  private static void run(String url, String profiles) {
    try (ConfigurableApplicationContext context =
        new SpringApplication(Application.class)
            .run(
                "--spring.config.location=classpath:/application.yml",
                "--app.migrate.exit-on-completion=false",
                "--spring.profiles.active=" + profiles,
                "--SPRING_DATASOURCE_URL=" + url,
                "--SPRING_DATASOURCE_USERNAME=" + PostgresContainer.username(),
                "--SPRING_DATASOURCE_PASSWORD=" + PostgresContainer.password())) {
      assertThat(context.isActive()).isTrue();
    }
  }

  private static Connection connect(String url) throws SQLException {
    return DriverManager.getConnection(
        url, PostgresContainer.username(), PostgresContainer.password());
  }

  private static long count(String url, String table) throws SQLException {
    try (Connection connection = connect(url);
        var statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
