package com.challenge.order_processing_api.controller;

import com.challenge.order_processing_api.dto.request.CreateOrderRequest;
import com.challenge.order_processing_api.dto.response.OrderResponse;
import com.challenge.order_processing_api.model.enums.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Pedidos", description = "Criação, consulta e acompanhamento de pedidos.")
public interface OrderApi {

    @Operation(summary = "Criar pedido", description = "Persiste o pedido e agenda seu processamento assíncrono.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pedido recebido para processamento."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
            @ApiResponse(responseCode = "500", description = "Erro interno ao processar o pedido.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    ResponseEntity<OrderResponse> create(CreateOrderRequest request);

    @Operation(summary = "Listar pedidos", description = "Lista pedidos com paginação, ordenação e filtros opcionais.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de pedidos."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
            @ApiResponse(responseCode = "500", description = "Erro interno ao listar pedidos.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    Page<OrderResponse> findAll(
            @Parameter(description = "Filtrar pelo status do processamento.") OrderStatus status,
            @Parameter(description = "Limite inferior inclusivo em ISO-8601.") Instant createdFrom,
            @Parameter(description = "Limite superior inclusivo em ISO-8601.") Instant createdTo,
            @ParameterObject Pageable pageable);

    @Operation(summary = "Consultar pedido", description = "Retorna os dados e o status atual de um pedido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado."),
            @ApiResponse(responseCode = "400", description = "Identificador inválido.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
            @ApiResponse(responseCode = "500", description = "Erro interno ao consultar pedido.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    })
    ResponseEntity<OrderResponse> findById(UUID id);
}
