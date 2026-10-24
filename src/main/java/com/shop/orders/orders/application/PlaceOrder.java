package com.shop.orders.orders.application;

import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderLine;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.RequestedItem;
import com.shop.orders.sharedkernel.Result;
import com.shop.orders.sharedkernel.port.IdGenerator;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Passe une commande : lit chaque produit dans le catalogue, copie nom, prix et devise, puis
 * enregistre l'agrégat en une seule écriture. Les lectures précèdent l'écriture.
 */
public final class PlaceOrder {

  private final ProductCatalog catalog;
  private final OrderRepository repository;
  private final Clock clock;
  private final IdGenerator idGenerator;

  public PlaceOrder(
      ProductCatalog catalog, OrderRepository repository, Clock clock, IdGenerator idGenerator) {
    this.catalog = catalog;
    this.repository = repository;
    this.clock = clock;
    this.idGenerator = idGenerator;
  }

  public Result<Order> execute(PlaceOrderCommand command) {
    if (Order.checkItems(command.items()) instanceof Result.Failure<List<RequestedItem>> invalid) {
      return new Result.Failure<>(invalid.error());
    }
    List<OrderLine> lines = new ArrayList<>();
    for (RequestedItem item : command.items()) {
      switch (toLine(item)) {
        case Result.Failure<OrderLine> failure -> {
          return new Result.Failure<>(failure.error());
        }
        case Result.Success<OrderLine> success -> lines.add(success.value());
      }
    }
    Result<Order> placed =
        Order.place(
            new OrderId(idGenerator.next()),
            command.customerId(),
            clock.instant().truncatedTo(ChronoUnit.MICROS),
            lines);
    if (placed instanceof Result.Success<Order> success) {
      repository.save(success.value());
    }
    return placed;
  }

  private Result<OrderLine> toLine(RequestedItem item) {
    return catalog.find(item.productId()).flatMap(product -> lineFor(product, item.quantity()));
  }

  private static Result<OrderLine> lineFor(CatalogProduct product, int quantity) {
    if (product.stock() < quantity) {
      return new Result.Failure<>(OrderErrors.insufficientStock());
    }
    return new Result.Success<>(
        new OrderLine(product.id(), product.name(), product.price(), quantity));
  }
}
