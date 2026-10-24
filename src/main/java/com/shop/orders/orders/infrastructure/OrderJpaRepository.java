package com.shop.orders.orders.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

  @EntityGraph(attributePaths = "lines")
  Optional<OrderEntity> findByIdAndCustomerId(UUID id, UUID customerId);

  @Query(
      """
      select new com.shop.orders.orders.infrastructure.OrderSummaryRow(
          o.id, o.createdAt, o.currency, o.totalAmount, o.itemCount)
      from OrderEntity o
      where o.customerId = :customerId
      order by o.createdAt desc, o.id desc
      """)
  List<OrderSummaryRow> firstPage(@Param("customerId") UUID customerId, Limit limit);

  @Query(
      """
      select new com.shop.orders.orders.infrastructure.OrderSummaryRow(
          o.id, o.createdAt, o.currency, o.totalAmount, o.itemCount)
      from OrderEntity o
      where o.customerId = :customerId
        and (o.createdAt < :createdAt or (o.createdAt = :createdAt and o.id < :id))
      order by o.createdAt desc, o.id desc
      """)
  List<OrderSummaryRow> pageAfter(
      @Param("customerId") UUID customerId,
      @Param("createdAt") Instant createdAt,
      @Param("id") UUID id,
      Limit limit);
}
