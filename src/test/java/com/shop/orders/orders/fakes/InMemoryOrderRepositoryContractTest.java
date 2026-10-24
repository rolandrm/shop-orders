package com.shop.orders.orders.fakes;

import com.shop.orders.orders.contract.OrderRepositoryContract;
import com.shop.orders.orders.domain.OrderRepository;

class InMemoryOrderRepositoryContractTest extends OrderRepositoryContract {

  private final InMemoryOrderRepository repository = new InMemoryOrderRepository();

  @Override
  protected OrderRepository repository() {
    return repository;
  }
}
