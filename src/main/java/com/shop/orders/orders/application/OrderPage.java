package com.shop.orders.orders.application;

import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderSummary;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Page de commandes ; {@code nextCursor} est absent sur la dernière page. */
public record OrderPage(List<OrderSummary> items, @Nullable OrderCursor nextCursor) {}
