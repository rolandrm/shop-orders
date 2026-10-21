package com.shop.orders.platform.web;

import java.util.List;

/** Format d'erreur unique de l'API. */
public record ApiError(
    int statusCode, String code, String message, List<?> details, String traceId) {}
