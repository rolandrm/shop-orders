package com.shop.orders.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ShopApiPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @EnableConfigurationProperties(ShopApiProperties.class)
  static class Config {}

  @Test
  void missingBaseUrlFailsContextNamingTheVariable() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasFailed();
          assertThat(ctx.getStartupFailure()).hasStackTraceContaining("SHOP_API_BASE_URL");
        });
  }

  @Test
  void blankBaseUrlFailsContextNamingTheVariable() {
    runner
        .withPropertyValues("app.shop-api.base-url=")
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure()).hasStackTraceContaining("SHOP_API_BASE_URL");
            });
  }

  @Test
  void baseUrlProvidedBindsWithDefaultTimeouts() {
    runner
        .withPropertyValues("app.shop-api.base-url=http://shop-api.test.invalid")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              ShopApiProperties properties = ctx.getBean(ShopApiProperties.class);
              assertThat(properties.baseUrl()).isEqualTo("http://shop-api.test.invalid");
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
              assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
            });
  }
}
