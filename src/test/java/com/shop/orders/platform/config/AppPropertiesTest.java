package com.shop.orders.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @EnableConfigurationProperties(AppProperties.class)
  static class Config {}

  @Test
  void blankServiceNameFailsContext() {
    runner
        .withPropertyValues("app.service-name=")
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure()).hasStackTraceContaining("serviceName");
            });
  }

  @Test
  void missingServiceNameFailsContext() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasFailed();
          assertThat(ctx.getStartupFailure()).hasStackTraceContaining("serviceName");
        });
  }

  @Test
  void validServiceNameStarts() {
    runner
        .withPropertyValues("app.service-name=shop-orders")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(AppProperties.class).serviceName()).isEqualTo("shop-orders");
            });
  }
}
