package com.shop.orders.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.PostgresContainer;
import com.shop.orders.orders.contract.OrderRepositoryContract;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Même suite de contrat que {@code InMemoryOrderRepositoryContractTest}, contre PostgreSQL. Sans
 * transaction de test : chaque appel écrit et relit réellement en base.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({PostgresContainer.class, JpaOrderRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaOrderRepositoryContractIT extends OrderRepositoryContract {

  @Autowired JpaOrderRepository repository;

  @Autowired OrderJpaRepository jpa;

  @BeforeEach
  void emptyTables() {
    jpa.deleteAll();
  }

  @Override
  protected OrderRepository repository() {
    return repository;
  }

  @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;

  @Test
  public void lineNumbersStartAtOne() {
    Order order = order(1, C1, Instant.parse("2026-10-22T10:00:00Z"));
    repository.save(order);

    var numbers =
        jdbc.sql("SELECT line_number FROM orders_order_line WHERE order_id = :id ORDER BY 1")
            .param("id", order.id().value())
            .query(Integer.class)
            .list();

    assertThat(numbers).containsExactly(1, 2);
  }

  @Test
  public void versionStartsAtZeroAndIsReadBackUnchanged() {
    Order order = order(1, C1, Instant.parse("2026-10-22T10:00:00Z"));
    repository.save(order);

    OrderEntity stored = jpa.findByIdAndCustomerId(order.id().value(), C1.value()).orElseThrow();

    assertThat(stored.version()).isEqualTo(0L);
    assertThat(stored.itemCount()).isEqualTo(2);
  }
}
