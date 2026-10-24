package com.shop.orders.orders.web;

import com.shop.orders.sharedkernel.Money;
import io.swagger.v3.oas.annotations.media.Schema;

/** Montant d'argent dans l'API : chaîne à deux décimales et devise. */
@Schema(description = "Montant d'argent : chaîne décimale exacte et devise ISO 4217.")
record MoneyResponse(
    @Schema(
            description = "Montant en notation décimale, toujours avec deux décimales.",
            pattern = "^\\d{1,15}\\.\\d{2}$",
            example = "20.00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String amount,
    @Schema(
            description = "Code de la devise du montant.",
            allowableValues = {"EUR", "USD", "GBP"},
            example = "EUR",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String currency) {

  static MoneyResponse from(Money money) {
    return new MoneyResponse(money.amountAsString(), money.currency().getCurrencyCode());
  }
}
