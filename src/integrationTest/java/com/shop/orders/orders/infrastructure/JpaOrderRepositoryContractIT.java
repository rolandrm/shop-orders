package com.shop.orders.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.PostgresContainer;
import com.shop.orders.orders.contract.OrderRepositoryContract;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderLine;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
  public void productNameOf200CharactersIsStoredAndReadBackWhole() {
    String name = "a".repeat(200);
    OrderId id = new OrderId(new UUID(0x018f000000007000L, 0x8000000000000777L));
    OrderLine line =
        new OrderLine(
            new ProductId(new UUID(0x018f000000007000L, 0x8000000000000778L)),
            name,
            Money.of("10.00", "EUR"),
            1);
    Order order =
        ((Result.Success<Order>)
                Order.place(id, C1, Instant.parse("2026-10-22T10:00:00Z"), List.of(line)))
            .value();
    repository.save(order);

    Order read = repository.findByIdAndCustomer(id, C1).orElseThrow();

    assertThat(read.lines()).hasSize(1);
    assertThat(read.lines().getFirst().productName()).isEqualTo(name);
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
