package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class RequiredEnvironmentTest {

  private static final String[] BASE_ARGS = {
    "--spring.config.location=classpath:/application.yml", "--server.port=0"
  };

  @Test
  void startupFailsNamingMissingVariable() {
    assertThatThrownBy(() -> SpringApplication.run(Application.class, BASE_ARGS))
        .hasStackTraceContaining("APP_SERVICE_NAME");
  }

  @Test
  void startupSucceedsWhenVariableIsProvided() {
    String[] args = new String[BASE_ARGS.length + 1];
    System.arraycopy(BASE_ARGS, 0, args, 0, BASE_ARGS.length);
    args[BASE_ARGS.length] = "--APP_SERVICE_NAME=shop-orders";
    try (ConfigurableApplicationContext ctx = SpringApplication.run(Application.class, args)) {
      assertThat(ctx.isRunning()).isTrue();
    }
  }
}
