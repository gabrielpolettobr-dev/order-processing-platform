package com.challenge.order_processing_api.service;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingService.class);

    private final OrderProcessingStateService processingStateService;
    private final Duration processingDelay;
    private final String failureDescriptionPrefix;

    public OrderProcessingService(
            OrderProcessingStateService processingStateService,
            @Value("${orders.processing.delay:PT1S}") Duration processingDelay,
            @Value("${orders.processing.fail-item-description-prefix:}") String failureDescriptionPrefix) {
        this.processingStateService = processingStateService;
        this.processingDelay = processingDelay;
        this.failureDescriptionPrefix = failureDescriptionPrefix;
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
                throw new IllegalStateException("Pedido sem itens não pode ser processado.");
            }
            if (!failureDescriptionPrefix.isBlank()
                    && processingStateService.hasItemDescriptionStartingWith(orderId, failureDescriptionPrefix)) {
                throw new IllegalStateException("Falha de processamento simulada pela configuração.");
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
            throw new IllegalStateException("Processamento do pedido foi interrompido.", exception);
        }
    }
}
