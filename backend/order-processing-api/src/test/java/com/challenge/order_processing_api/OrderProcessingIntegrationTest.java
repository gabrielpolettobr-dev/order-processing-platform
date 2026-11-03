package com.challenge.order_processing_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.challenge.order_processing_api.dto.request.CreateOrderItemRequest;
import com.challenge.order_processing_api.dto.request.CreateOrderRequest;
import com.challenge.order_processing_api.dto.response.OrderMessage;
import com.challenge.order_processing_api.dto.response.OrderResponse;
import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.model.Order;
import com.challenge.order_processing_api.model.OrderItem;
import com.challenge.order_processing_api.model.OrderStatus;
import com.challenge.order_processing_api.model.OutboxEvent;
import com.challenge.order_processing_api.repository.OrderRepository;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import com.challenge.order_processing_api.service.ClaimedOutboxEvent;
import com.challenge.order_processing_api.service.OutboxClaimService;
import com.challenge.order_processing_api.service.OrderProcessingStateService;
import com.challenge.order_processing_api.messaging.OutboxPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "orders.processing.delay=PT0S",
        "orders.processing.fail-item-description-prefix=FAIL-",
        "orders.outbox.poll-interval-ms=60000",
        "orders.rabbitmq.retry.max-attempts=3",
        "orders.rabbitmq.retry.initial-interval-ms=10",
        "orders.rabbitmq.retry.max-interval-ms=20"
})
class OrderProcessingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("orders_test")
            .withUsername("orders_test")
            .withPassword("orders_test");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @DynamicPropertySource
    static void configureDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OrderProcessingStateService processingStateService;

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrdersProperties ordersProperties;

    @BeforeEach
    void cleanDatabaseAndQueues() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM orders");
        amqpAdmin.purgeQueue("orders.queue", true);
        amqpAdmin.purgeQueue("orders.dlq", true);
    }

    @Test
    void flywayMigrationAndOrderLifecycleWorkAgainstPostgresAndRabbitMq() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("2");

        ResponseEntity<OrderResponse> created = restTemplate.postForEntity(
                "/api/orders",
                request("Keyboard", "integration@example.com"),
                OrderResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getBody()).isNotNull();
        UUID orderId = created.getBody().id();
        assertThat(created.getBody().totalAmount()).isEqualByComparingTo("300.00");

        outboxPublisher.publishPendingEvents();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<OrderResponse> result = restTemplate.getForEntity(
                    "/api/orders/" + orderId, OrderResponse.class);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().status()).isEqualTo(OrderStatus.COMPLETED);
        });

        assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> event.getAggregateId().equals(orderId))
                .singleElement()
                .extracting(OutboxEvent::getPublishedAt)
                .isNotNull();
    }

    @Test
    void bindsOrderConfigurationFromExternalProperties() {
        assertThat(ordersProperties.processing().delay()).isEqualTo(Duration.ZERO);
        assertThat(ordersProperties.processing().failItemDescriptionPrefix()).isEqualTo("FAIL-");
        assertThat(ordersProperties.pagination().maxPageSize()).isEqualTo(100);
        assertThat(ordersProperties.outbox().pollIntervalMs()).isEqualTo(60_000);
        assertThat(ordersProperties.rabbitmq().retry().maxAttempts()).isEqualTo(3);
    }

    @Test
    void retriesFailedProcessingMarksOrderFailedAndRoutesMessageToDlq() {
        ResponseEntity<OrderResponse> created = restTemplate.postForEntity(
                "/api/orders",
                request("FAIL-simulated-error", "failure@example.com"),
                OrderResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        outboxPublisher.publishPendingEvents();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ResponseEntity<OrderResponse> result = restTemplate.getForEntity(
                    "/api/orders/" + created.getBody().id(), OrderResponse.class);
            assertThat(result.getBody().status()).isEqualTo(OrderStatus.FAILED);
            assertThat(amqpAdmin.getQueueInfo("orders.dlq").getMessageCount()).isEqualTo(1);
        });

        Object deadLetter = rabbitTemplate.receiveAndConvert("orders.dlq", 1000);
        assertThat(deadLetter).isInstanceOf(OrderMessage.class);
        assertThat(((OrderMessage) deadLetter).orderId()).isEqualTo(created.getBody().id());
    }

    @Test
    void concurrentConsumersOnlyClaimAnOrderOnce() throws Exception {
        UUID orderId = orderRepository.saveAndFlush(new Order(
                "Concurrent Test",
                "concurrent@example.com",
                List.of(new OrderItem("Test item", 1, new BigDecimal("10.00"))))).getId();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return processingStateService.markProcessing(orderId);
            });
            var second = executor.submit(() -> {
                start.await();
                return processingStateService.markProcessing(orderId);
            });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PROCESSING);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsPageSizesAboveConfiguredLimitAndUnsupportedSorts() {
        ResponseEntity<String> tooLarge = restTemplate.getForEntity("/api/orders?size=101", String.class);
        ResponseEntity<String> unsupportedSort = restTemplate.getForEntity(
                "/api/orders?sort=items.description,asc", String.class);

        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unsupportedSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listsOrdersWithStatusFilterPaginationAndSorting() {
        ResponseEntity<OrderResponse> first = restTemplate.postForEntity(
                "/api/orders", request("First list item", "list-first@example.com"), OrderResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<OrderResponse> second = restTemplate.postForEntity(
                "/api/orders", request("Second list item", "list-second@example.com"), OrderResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        outboxPublisher.publishPendingEvents();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(orderRepository.findById(first.getBody().id()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.COMPLETED);
            assertThat(orderRepository.findById(second.getBody().id()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.COMPLETED);
        });

        ResponseEntity<JsonNode> page = restTemplate.getForEntity(
                "/api/orders?status=COMPLETED&page=0&size=1&sort=createdAt,desc",
                JsonNode.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody().path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.getBody().path("numberOfElements").asInt()).isEqualTo(1);
        assertThat(page.getBody().path("content").get(0).path("status").asText())
                .isEqualTo(OrderStatus.COMPLETED.name());
    }

    @Test
    void rejectsInvalidOrderDataWithConsistentValidationError() {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "Invalid Customer",
                "not-an-email",
                List.of(new CreateOrderItemRequest("Invalid quantity", 0, new BigDecimal("10.00"))));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/orders", invalidRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"type\":\"about:blank\"")
                .contains("\"title\":\"Bad Request\"")
                .contains("\"status\":400")
                .contains("\"detail\"")
                .contains("\"instance\"")
                .contains("\"fieldErrors\"");
    }

    @Test
    void documentsTheStandardErrorResponseInOpenApi() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);
        JsonNode openApi = objectMapper.readTree(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.at("/components/schemas/ProblemDetail").isObject()).isTrue();
        assertThat(openApi.at("/paths/~1api~1orders/post/responses/400/content/application~1problem+json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ProblemDetail");
    }

    @Test
    void outboxClaimsPreventConcurrentPublishingAndRecoverAfterLeaseExpiry() throws Exception {
        Order order = orderRepository.saveAndFlush(new Order(
                "Outbox Concurrency",
                "outbox@example.com",
                List.of(new OrderItem("Test item", 1, new BigDecimal("10.00")))));
        outboxEventRepository.saveAndFlush(new OutboxEvent(
                order.getId(),
                "ORDER_CREATED",
                "{\"orderId\":\"" + order.getId() + "\"}"));

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstPublisher = executor.submit(() -> {
                start.await();
                return outboxClaimService.claimNext();
            });
            var secondPublisher = executor.submit(() -> {
                start.await();
                return outboxClaimService.claimNext();
            });
            start.countDown();

            var firstClaim = firstPublisher.get(10, TimeUnit.SECONDS);
            var secondClaim = secondPublisher.get(10, TimeUnit.SECONDS);
            assertThat(firstClaim.isPresent() ^ secondClaim.isPresent()).isTrue();
            ClaimedOutboxEvent originalClaim = firstClaim.orElseGet(secondClaim::orElseThrow);
            assertThat(outboxClaimService.claimNext()).isEmpty();

            jdbcTemplate.update(
                    "UPDATE outbox_events SET claim_expires_at = now() - interval '1 second' WHERE id = ?",
                    originalClaim.id());
            ClaimedOutboxEvent recoveredClaim = outboxClaimService.claimNext().orElseThrow();
            assertThat(recoveredClaim.claimToken()).isNotEqualTo(originalClaim.claimToken());
            assertThat(outboxClaimService.markPublished(originalClaim)).isFalse();
            assertThat(outboxClaimService.markPublished(recoveredClaim)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private CreateOrderRequest request(String description, String email) {
        return new CreateOrderRequest(
                "Integration Customer",
                email,
                List.of(new CreateOrderItemRequest(description, 2, new BigDecimal("150.00"))));
    }
}
