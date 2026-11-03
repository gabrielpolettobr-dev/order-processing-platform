package com.challenge.order_processing_api.mapper;

import com.challenge.order_processing_api.dto.request.CreateOrderItemRequest;
import com.challenge.order_processing_api.dto.request.CreateOrderRequest;
import com.challenge.order_processing_api.dto.response.OrderItemResponse;
import com.challenge.order_processing_api.dto.response.OrderResponse;
import com.challenge.order_processing_api.model.entity.Order;
import com.challenge.order_processing_api.model.entity.OrderItem;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    OrderItemResponse toResponse(OrderItem item);

    default Order toEntity(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(this::toEntity)
                .toList();
        return new Order(request.customerName(), request.customerEmail(), items);
    }

    default OrderItem toEntity(CreateOrderItemRequest request) {
        return new OrderItem(request.description(), request.quantity(), request.unitPrice());
    }
}
