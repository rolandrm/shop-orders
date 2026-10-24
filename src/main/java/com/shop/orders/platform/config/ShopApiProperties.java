package com.shop.orders.platform.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.shop-api")
@Validated
public record ShopApiProperties(
    @NotBlank(message = "la variable d'environnement SHOP_API_BASE_URL est obligatoire")
        String baseUrl,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("3s") Duration readTimeout) {}
