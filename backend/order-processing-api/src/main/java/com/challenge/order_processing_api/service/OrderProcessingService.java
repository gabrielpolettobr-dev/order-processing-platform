package com.challenge.order_processing_api.service;

import com.challenge.order_processing_api.config.OrdersProperties;
import com.challenge.order_processing_api.exception.OrderProcessingException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingService.class);

    private final OrderProcessingStateService processingStateService;
    private final Duration processingDelay;
    private final String failureDescriptionPrefix;

    public OrderProcessingService(
            OrderProcessingStateService processingStateService,
            OrdersProperties ordersProperties) {
        this.processingStateService = processingStateService;
        this.processingDelay = ordersProperties.processing().delay();
        this.failureDescriptionPrefix = ordersProperties.processing().failItemDescriptionPrefix();
    }

    public void process(UUID orderId) {
        if (!processingStateService.markProcessing(orderId)) {
            logger.info("Pedido {} já está em processamento ou finalizado; mensagem duplicada ignorada.", orderId);
            return;
        }

        logger.info("Iniciando processamento do pedido {}.", orderId);
        try {
            simulateProcessing();
            if (!processingStateService.hasItems(orderId)) {
                throw new OrderProcessingException("Pedido sem itens não pode ser processado.");
            }
            if (!failureDescriptionPrefix.isBlank()
                    && processingStateService.hasItemDescriptionStartingWith(orderId, failureDescriptionPrefix)) {
                throw new OrderProcessingException("Falha de processamento simulada pela configuração.");
            }
            processingStateService.markCompleted(orderId);
            logger.info("Processamento do pedido {} concluído.", orderId);
        } catch (RuntimeException exception) {
            processingStateService.markReadyForRetry(orderId);
            throw exception;
        }
    }

    private void simulateProcessing() {
        if (processingDelay.isZero() || processingDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(processingDelay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OrderProcessingException("Processamento do pedido foi interrompido.", exception);
        }
    }
}
