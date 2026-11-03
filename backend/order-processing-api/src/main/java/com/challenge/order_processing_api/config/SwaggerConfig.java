package com.challenge.order_processing_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    OpenAPI orderProcessingOpenApi() {
        Schema<?> fieldErrorSchema = new ObjectSchema()
                .addProperty("field", new StringSchema().description("Campo inválido."))
                .addProperty("message", new StringSchema().description("Motivo da falha de validação."));

        Schema<?> problemDetailSchema = new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri").description("URI que identifica o tipo do problema."))
                .addProperty("title", new StringSchema().description("Resumo legível do problema."))
                .addProperty("status", new IntegerSchema().description("Código HTTP."))
                .addProperty("detail", new StringSchema().description("Explicação específica desta ocorrência."))
                .addProperty("instance", new StringSchema().format("uri").description("URI da requisição com erro."))
                .addProperty("timestamp", new StringSchema().format("date-time").description("Instante em que ocorreu."))
                .addProperty("fieldErrors", new ArraySchema().items(fieldErrorSchema)
                        .description("Extensão com erros de validação por campo, quando aplicável."))
                .required(List.of("title", "status", "detail", "instance"));

        return new OpenAPI()
                .components(new Components().addSchemas("ProblemDetail", problemDetailSchema))
                .info(new Info()
                        .title("Order Processing Platform API")
                        .description("API REST para criação, consulta e processamento assíncrono de pedidos.")
                        .version("v1")
                        .license(new License().name("MIT")));
    }
}
