package com.shop.orders.platform.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app")
@Validated
public record AppProperties(
    @NotBlank(message = "la variable d'environnement APP_SERVICE_NAME est obligatoire")
        String serviceName) {}
