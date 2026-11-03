package com.challenge.order_processing_api.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("itemPosition ASC")
    @Getter(AccessLevel.NONE)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(String customerName, String customerEmail, List<OrderItem> items) {
        this.id = UUID.randomUUID();
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.status = OrderStatus.RECEIVED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        items.forEach(this::addItem);
        this.totalAmount = this.items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (this.totalAmount.precision() > 19) {
            throw new IllegalArgumentException("O valor total excede o limite permitido.");
        }
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    private void addItem(OrderItem item) {
        item.attachTo(this, items.size());
        items.add(item);
    }

    public void changeStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

}
