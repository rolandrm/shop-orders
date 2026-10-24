package com.shop.orders.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.orders.PostgresContainer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Écrit directement en base, hors des règles du domaine : chaque écriture fautive ne viole qu'une
 * contrainte de {@code V1}, dont le nom doit figurer dans l'erreur PostgreSQL. Chaque test est
 * annulé à la fin (transaction de test).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(PostgresContainer.class)
class OrdersSchemaConstraintsIT {

  private static final String INSERT_ORDER =
      """
      INSERT INTO orders_order (id, customer_id, created_at, currency, total_amount, item_count, version)
      VALUES (:id, :customerId, :createdAt, :currency, :total, :itemCount, 0)
      """;

  private static final String INSERT_LINE =
      """
      INSERT INTO orders_order_line (order_id, line_number, product_id, product_name, unit_price_amount, quantity, line_total_amount)
      VALUES (:orderId, :lineNumber, :productId, 'Clavier', :unitPrice, :quantity, :lineTotal)
      """;

  @Autowired JdbcClient jdbc;

  private UUID insertOrder(String currency, String total, int itemCount) {
    UUID id = UUID.randomUUID();
    jdbc.sql(INSERT_ORDER)
        .param("id", id)
        .param("customerId", UUID.randomUUID())
        .param("createdAt", OffsetDateTime.of(2026, 10, 22, 10, 0, 0, 0, ZoneOffset.UTC))
        .param("currency", currency)
        .param("total", new BigDecimal(total))
        .param("itemCount", itemCount)
        .update();
    return id;
  }

  private void insertLine(
      UUID orderId,
      int lineNumber,
      UUID productId,
      String unitPrice,
      int quantity,
      String lineTotal) {
    jdbc.sql(INSERT_LINE)
        .param("orderId", orderId)
        .param("lineNumber", lineNumber)
        .param("productId", productId)
        .param("unitPrice", new BigDecimal(unitPrice))
        .param("quantity", quantity)
        .param("lineTotal", new BigDecimal(lineTotal))
        .update();
  }

  private void insertValidLine(UUID orderId, int lineNumber, UUID productId) {
    insertLine(orderId, lineNumber, productId, "10.00", 2, "20.00");
  }

  private static void assertRejectedBy(Runnable write, String constraint) {
    assertThatThrownBy(write::run).hasMessageContaining(constraint);
  }

  @Test
  void validReferenceWritePasses() {
    UUID orderId = insertOrder("EUR", "20.00", 1);
    insertValidLine(orderId, 1, UUID.randomUUID());

    Integer lines =
        jdbc.sql("SELECT count(*) FROM orders_order_line WHERE order_id = :id")
            .param("id", orderId)
            .query(Integer.class)
            .single();
    assertThat(lines).isEqualTo(1);
  }

  @Test
  void itemCountZeroIsRejected() {
    assertRejectedBy(() -> insertOrder("EUR", "0.00", 0), "ck_orders_order_item_count");
  }

  @Test
  void itemCountFiftyOneIsRejected() {
    assertRejectedBy(() -> insertOrder("EUR", "0.00", 51), "ck_orders_order_item_count");
  }

  @Test
  void negativeTotalIsRejected() {
    assertRejectedBy(() -> insertOrder("EUR", "-0.01", 1), "ck_orders_order_total_amount");
  }

  @Test
  void lowercaseCurrencyIsRejected() {
    assertRejectedBy(() -> insertOrder("eur", "1.00", 1), "ck_orders_order_currency");
  }

  @Test
  void lineNumberZeroIsRejected() {
    UUID orderId = insertOrder("EUR", "20.00", 1);
    assertRejectedBy(
        () -> insertValidLine(orderId, 0, UUID.randomUUID()), "ck_orders_order_line_line_number");
  }

  @Test
  void quantityZeroIsRejected() {
    UUID orderId = insertOrder("EUR", "20.00", 1);
    assertRejectedBy(
        () -> insertLine(orderId, 1, UUID.randomUUID(), "10.00", 0, "0.00"),
        "ck_orders_order_line_quantity");
  }

  @Test
  void quantityOneHundredAndOneIsRejected() {
    UUID orderId = insertOrder("EUR", "20.00", 1);
    assertRejectedBy(
        () -> insertLine(orderId, 1, UUID.randomUUID(), "10.00", 101, "1010.00"),
        "ck_orders_order_line_quantity");
  }

  @Test
  void negativeUnitPriceIsRejected() {
    UUID orderId = insertOrder("EUR", "20.00", 1);
    // Total cohérent (-1.00 x 2 = -2.00) : seule la contrainte du prix est violée.
    assertRejectedBy(
        () -> insertLine(orderId, 1, UUID.randomUUID(), "-1.00", 2, "-2.00"),
        "\"ck_orders_order_line_unit_price_amount\"");
  }

  @Test
  void inconsistentLineTotalIsRejected() {
    UUID orderId = insertOrder("EUR", "20.00", 1);
    assertRejectedBy(
        () -> insertLine(orderId, 1, UUID.randomUUID(), "10.00", 2, "21.00"),
        "\"ck_orders_order_line_line_total\"");
  }

  @Test
  void sameProductTwiceInAnOrderIsRejected() {
    UUID orderId = insertOrder("EUR", "40.00", 2);
    UUID productId = UUID.randomUUID();
    insertValidLine(orderId, 1, productId);
    assertRejectedBy(
        () -> insertValidLine(orderId, 2, productId), "uq_orders_order_line_order_product");
  }
}
