package com.shop.orders.orders.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

/** Ligne de commande persistée dans {@code orders_order_line}. */
@Embeddable
@SuppressWarnings("NullAway.Init")
class OrderLineEmbeddable {

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPriceAmount;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "line_total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal lineTotalAmount;

  protected OrderLineEmbeddable() {}

  OrderLineEmbeddable(
      UUID productId,
      String productName,
      BigDecimal unitPriceAmount,
      int quantity,
      BigDecimal lineTotalAmount) {
    this.productId = productId;
    this.productName = productName;
    this.unitPriceAmount = unitPriceAmount;
    this.quantity = quantity;
    this.lineTotalAmount = lineTotalAmount;
  }

  UUID productId() {
    return productId;
  }

  String productName() {
    return productName;
  }

  BigDecimal unitPriceAmount() {
    return unitPriceAmount;
  }

  int quantity() {
    return quantity;
  }

  BigDecimal lineTotalAmount() {
    return lineTotalAmount;
  }
}
