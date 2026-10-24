package com.shop.orders.orders.domain;

/** Produit et quantité demandés par le client. */
public record RequestedItem(ProductId productId, int quantity) {}
