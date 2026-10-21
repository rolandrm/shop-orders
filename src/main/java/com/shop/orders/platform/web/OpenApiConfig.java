package com.shop.orders.platform.web;

import com.shop.orders.platform.config.AppProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String JSON = "application/json";

  @Bean
  OpenAPI openApi(AppProperties properties, BuildProperties buildProperties) {
    Schema<?> apiError =
        new ObjectSchema()
            .description("Format d'erreur unique de l'API.")
            .addProperty("statusCode", new IntegerSchema().description("Code HTTP."))
            .addProperty("code", new StringSchema().description("Code d'erreur stable."))
            .addProperty("message", new StringSchema().description("Message lisible."))
            .addProperty(
                "details",
                new ArraySchema()
                    .items(new ObjectSchema())
                    .description("Détails, par exemple les champs invalides."))
            .addProperty(
                "traceId",
                new StringSchema().description("Identifiant de corrélation de la requête."));

    Operation operation =
        new Operation()
            .summary("Télécharger le contrat OpenAPI du service")
            .responses(
                new ApiResponses()
                    .addApiResponse(
                        "200",
                        new ApiResponse()
                            .description("Le contrat OpenAPI du service, au format JSON.")
                            .content(
                                new Content()
                                    .addMediaType(
                                        JSON, new MediaType().schema(new ObjectSchema()))))
                    .addApiResponse(
                        "500",
                        new ApiResponse()
                            .description("Erreur interne lors de la génération du contrat.")
                            .content(
                                new Content()
                                    .addMediaType(
                                        JSON,
                                        new MediaType()
                                            .schema(
                                                new Schema<>()
                                                    .$ref("#/components/schemas/ApiError"))))));

    return new OpenAPI()
        .info(
            new Info()
                .title(properties.serviceName())
                .version(buildProperties.getVersion())
                .description("Contrat d'API du service " + properties.serviceName() + "."))
        .paths(new Paths().addPathItem("/docs/openapi.json", new PathItem().get(operation)))
        .components(new Components().addSchemas("ApiError", apiError));
  }
}
