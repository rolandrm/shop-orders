package com.shop.orders.orders.application;

import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.RequestedItem;
import java.util.List;

/** Demande de commande d'un client. */
public record PlaceOrderCommand(CustomerId customerId, List<RequestedItem> items) {}
