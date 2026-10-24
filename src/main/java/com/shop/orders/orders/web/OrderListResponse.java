package com.shop.orders.orders.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Page de commandes. */
@Schema(description = "Page de commandes d'un client, les plus récentes d'abord.")
@JsonInclude(JsonInclude.Include.NON_NULL)
record OrderListResponse(
    @Schema(
            description = "Résumés des commandes de la page.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<OrderSummaryResponse> items,
    @Schema(
            description =
                "Curseur opaque à renvoyer dans le paramètre cursor pour obtenir la page suivante ;"
                    + " absent sur la dernière page.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Nullable String nextCursor) {}
