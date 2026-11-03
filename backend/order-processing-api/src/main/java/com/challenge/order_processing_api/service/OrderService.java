package com.challenge.order_processing_api.service;

import com.challenge.order_processing_api.dto.request.CreateOrderRequest;
import com.challenge.order_processing_api.dto.response.OrderResponse;
import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.exception.InvalidOrderQueryException;
import com.challenge.order_processing_api.exception.OrderNotFoundException;
import com.challenge.order_processing_api.exception.OrderEventSerializationException;
import com.challenge.order_processing_api.mapper.OrderMapper;
import com.challenge.order_processing_api.messaging.dto.OrderMessage;
import com.challenge.order_processing_api.model.entity.Order;
import com.challenge.order_processing_api.model.entity.OutboxEvent;
import com.challenge.order_processing_api.model.enums.OrderStatus;
import com.challenge.order_processing_api.repository.OrderRepository;
import com.challenge.order_processing_api.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "createdAt", "updatedAt", "customerName", "customerEmail", "totalAmount", "status");

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;
    private final OrdersProperties.Pagination paginationProperties;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            OrderMapper orderMapper,
            OrdersProperties ordersProperties) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
        this.paginationProperties = ordersProperties.pagination();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = orderRepository.save(orderMapper.toEntity(request));
        try {
            String payload = objectMapper.writeValueAsString(new OrderMessage(order.getId()));
            outboxEventRepository.save(new OutboxEvent(order.getId(), "ORDER_CREATED", payload));
        } catch (JsonProcessingException exception) {
            throw new OrderEventSerializationException(
                    "Não foi possível serializar o evento do pedido.", exception);
        }
        logger.info("Pedido {} criado.", order.getId());
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        return orderRepository.findById(id)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(
            OrderStatus status,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable) {
        validatePageable(pageable);
        validateCreationPeriod(createdFrom, createdTo);

        return orderRepository.findAll(buildSpecification(status, createdFrom, createdTo), pageable)
                .map(orderMapper::toResponse);
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() > paginationProperties.maxPageSize()) {
            throw new InvalidOrderQueryException("O tamanho da página deve estar entre 1 e "
                    + paginationProperties.maxPageSize() + ".");
        }
        boolean hasUnsupportedSort = pageable.getSort().stream()
                .anyMatch(order -> !SORTABLE_FIELDS.contains(order.getProperty()));
        if (hasUnsupportedSort) {
            throw new InvalidOrderQueryException("Campo de ordenação não permitido.");
        }
    }

    private void validateCreationPeriod(Instant createdFrom, Instant createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidOrderQueryException("createdFrom deve ser anterior ou igual a createdTo.");
        }
    }

    private Specification<Order> buildSpecification(
            OrderStatus status,
            Instant createdFrom,
            Instant createdTo) {
        List<Specification<Order>> filters = new ArrayList<>();
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
        return Specification.allOf(filters);
    }
}
