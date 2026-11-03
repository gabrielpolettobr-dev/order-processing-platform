# Modelo de dados

```mermaid
erDiagram
    ORDERS ||--|{ ORDER_ITEMS : contains
    ORDERS ||--o{ OUTBOX_EVENTS : emits

    ORDERS {
        uuid id PK
        varchar customer_name
        varchar customer_email
        numeric total_amount
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }
    ORDER_ITEMS {
        uuid id PK
        uuid order_id FK
        integer item_position
        varchar description
        integer quantity
        numeric unit_price
        numeric subtotal
    }
    OUTBOX_EVENTS {
        uuid id PK
        uuid aggregate_id FK
        varchar event_type
        text payload
        timestamptz created_at
        timestamptz published_at
    }
```

O schema é criado por `src/main/resources/db/migration/V1__create_order_tables.sql`. Valores monetários usam `NUMERIC(19,2)`. `item_position` preserva a ordem dos itens enviada na criação. `published_at IS NULL` identifica eventos ainda pendentes na Outbox.
