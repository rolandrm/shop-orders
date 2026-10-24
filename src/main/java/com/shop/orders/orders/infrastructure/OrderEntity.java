package com.shop.orders.orders.infrastructure;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Commande persistée dans {@code orders_order} ; ne sort pas du package. */
@Entity
@Table(name = "orders_order")
@SuppressWarnings("NullAway.Init")
class OrderEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "item_count", nullable = false)
  private int itemCount;

  /** Nul avant l'insertion, 0 ensuite. */
  @Version
  @Column(name = "version", nullable = false)
  private @Nullable Long version;

  @ElementCollection
  @CollectionTable(name = "orders_order_line", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "line_number")
  @ListIndexBase(1)
  private List<OrderLineEmbeddable> lines = new ArrayList<>();

  protected OrderEntity() {}

  OrderEntity(
      UUID id,
      UUID customerId,
      Instant createdAt,
      String currency,
      BigDecimal totalAmount,
      List<OrderLineEmbeddable> lines) {
    this.id = id;
    this.customerId = customerId;
    this.createdAt = createdAt;
    this.currency = currency;
    this.totalAmount = totalAmount;
    this.itemCount = lines.size();
    this.lines = new ArrayList<>(lines);
  }

  UUID id() {
    return id;
  }

  UUID customerId() {
    return customerId;
  }

  Instant createdAt() {
    return createdAt;
  }

  String currency() {
    return currency;
  }

  BigDecimal totalAmount() {
    return totalAmount;
  }

  int itemCount() {
    return itemCount;
  }

  @Nullable Long version() {
    return version;
  }

  List<OrderLineEmbeddable> lines() {
    return lines;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof OrderEntity entity && id.equals(entity.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
