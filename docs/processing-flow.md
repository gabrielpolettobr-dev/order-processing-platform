# Fluxo de processamento

```mermaid
sequenceDiagram
    actor Client
    participant API as OrderController / OrderService
    participant DB as PostgreSQL
    participant Outbox as OutboxPublisher
    participant MQ as RabbitMQ
    participant Consumer as OrderConsumer
    participant Worker as OrderProcessingService

    Client->>API: POST /api/orders
    API->>API: Validar entrada e calcular valores
    API->>DB: Salvar pedido RECEIVED e evento Outbox
    DB-->>API: Commit da transação
    API-->>Client: 202 Accepted + pedido
    Outbox->>DB: Reservar evento em transação curta (lease)
    DB-->>Outbox: Commit da reserva
    Outbox->>MQ: Publicar fora da transação do banco
    MQ-->>Outbox: Confirmação de publicação
    Outbox->>DB: Marcar publicado em nova transação
    MQ->>Consumer: Entregar mensagem
    Consumer->>Worker: process(orderId)
    Worker->>DB: Marcar PROCESSING com lock
    Worker->>Worker: Executar processamento simulado
    Worker->>DB: Marcar COMPLETED

    alt Falha temporária
        Worker-->>MQ: Lançar erro para tentativa configurada
        MQ->>Consumer: Repetir consumo
    else Tentativas esgotadas
        MQ->>DB: Marcar pedido FAILED
        MQ->>MQ: Rejeitar e rotear para orders.dlq
    end
```

A requisição HTTP não espera pelo processamento. O evento Outbox é persistido junto com o pedido para evitar a janela de perda entre o commit do banco e a publicação. O reenvio após uma falha de confirmação pode duplicar a mensagem; o consumer ignora pedidos finalizados ou já reivindicados recentemente.

Cada reserva da Outbox expira após o intervalo configurado em `OUTBOX_CLAIM_TIMEOUT` (padrão `PT30S`). Se o publicador cair, outra instância pode reivindicar o evento após esse prazo. Uma falha de publicação mantém a reserva até expirar, evitando tentativas repetidas em loop no mesmo ciclo.
