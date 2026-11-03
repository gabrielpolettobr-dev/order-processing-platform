package com.challenge.order_processing_api.service;

import com.challenge.order_processing_api.dto.request.CreateOrderRequest;
import com.challenge.order_processing_api.dto.response.OrderMessage;
import com.challenge.order_processing_api.dto.response.OrderResponse;
import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.exception.OrderNotFoundException;
import com.challenge.order_processing_api.exception.InvalidOrderQueryException;
import com.challenge.order_processing_api.exception.OrderEventSerializationException;
import com.challenge.order_processing_api.model.Order;
import com.challenge.order_processing_api.model.OrderItem;
import com.challenge.order_processing_api.model.OutboxEvent;
import com.challenge.order_processing_api.model.OrderStatus;
import com.challenge.order_processing_api.repository.OrderRepository;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "createdAt", "updatedAt", "customerName", "customerEmail", "totalAmount", "status");

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OrdersProperties.Pagination paginationProperties;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            OrdersProperties ordersProperties) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.paginationProperties = ordersProperties.pagination();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        var items = request.items().stream()
                .map(item -> new OrderItem(item.description(), item.quantity(), item.unitPrice()))
                .toList();
        Order order = orderRepository.save(new Order(request.customerName(), request.customerEmail(), items));
        try {
            String payload = objectMapper.writeValueAsString(new OrderMessage(order.getId()));
            outboxEventRepository.save(new OutboxEvent(order.getId(), "ORDER_CREATED", payload));
        } catch (JsonProcessingException exception) {
            throw new OrderEventSerializationException(
                    "Não foi possível serializar o evento do pedido.", exception);
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(
            OrderStatus status,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() > paginationProperties.maxPageSize()) {
            throw new InvalidOrderQueryException("O tamanho da página deve estar entre 1 e "
                    + paginationProperties.maxPageSize() + ".");
        }
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidOrderQueryException(
                        "Ordenação não permitida para o campo: " + order.getProperty());
            }
        });
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidOrderQueryException("createdFrom deve ser anterior ou igual a createdTo.");
        }

        var filters = new ArrayList<Specification<Order>>();
        if (status != null) {
            filters.add((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        if (createdFrom != null) {
            filters.add((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
        }
        if (createdTo != null) {
            filters.add((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
        }
        return orderRepository.findAll(Specification.allOf(filters), pageable).map(OrderResponse::from);
    }
}
