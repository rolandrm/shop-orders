package com.shop.orders.orders.domain;

import java.util.UUID;

/** Identifiant d'un client (non vérifié : voir la spec). */
public record CustomerId(UUID value) {}
