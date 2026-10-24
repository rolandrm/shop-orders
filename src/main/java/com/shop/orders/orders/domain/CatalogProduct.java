package com.shop.orders.orders.domain;

import com.shop.orders.sharedkernel.Money;

/** Produit tel que lu dans le catalogue de shop-api. */
public record CatalogProduct(ProductId id, String name, Money price, int stock) {}
