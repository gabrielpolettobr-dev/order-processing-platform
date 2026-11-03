package com.challenge.order_processing_api.service;

import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.exception.OrderNotFoundException;
import com.challenge.order_processing_api.model.Order;
import com.challenge.order_processing_api.model.OrderStatus;
import com.challenge.order_processing_api.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProcessingStateService {

    private final OrderRepository orderRepository;
    private final Duration processingLockTimeout;

    public OrderProcessingStateService(OrderRepository orderRepository, OrdersProperties ordersProperties) {
        this.orderRepository = orderRepository;
        this.processingLockTimeout = ordersProperties.processing().lockTimeout();
    }

    @Transactional
    public boolean markProcessing(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.FAILED) {
            return false;
        }
        if (order.getStatus() == OrderStatus.PROCESSING
                && order.getUpdatedAt().isAfter(Instant.now().minus(processingLockTimeout))) {
            return false;
        }
        order.changeStatus(OrderStatus.PROCESSING);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean hasItems(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        return !order.getItems().isEmpty();
    }

    @Transactional
    public void markCompleted(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.changeStatus(OrderStatus.COMPLETED);
    }

    @Transactional
    public void markReadyForRetry(UUID orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PROCESSING) {
                order.changeStatus(OrderStatus.RECEIVED);
            }
        });
    }

    @Transactional
    public void markFailed(UUID orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.COMPLETED) {
                order.changeStatus(OrderStatus.FAILED);
            }
        });
    }

    @Transactional(readOnly = true)
    public boolean hasItemDescriptionStartingWith(UUID orderId, String prefix) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        return order.getItems().stream().anyMatch(item -> item.getDescription().startsWith(prefix));
    }
}
