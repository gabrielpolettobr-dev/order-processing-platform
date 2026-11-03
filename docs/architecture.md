# Arquitetura

O escopo desta etapa contém o backend e suas dependências. O frontend Angular será conectado depois.

```mermaid
flowchart LR
    Client[HTTP client / Swagger] --> Controller[OrderController]
    Controller -. contract and OpenAPI .-> Api[OrderApi]
    Controller --> Service[OrderService]
    Service --> OrderRepo[OrderRepository]
    Service --> OutboxRepo[OutboxEventRepository]
    OrderRepo --> PostgreSQL[(PostgreSQL)]
    OutboxRepo --> PostgreSQL
    Publisher[OutboxPublisher] --> OutboxRepo
    Publisher --> Exchange[orders.exchange]
    Exchange --> Queue[orders.queue]
    Queue --> Consumer[OrderConsumer]
    Consumer --> Processing[OrderProcessingService]
    Processing --> State[OrderProcessingStateService]
    State --> OrderRepo
    Queue -. retry limit exceeded .-> DLX[orders.dlx]
    DLX --> DLQ[orders.dlq]
```

`OrderApi` documenta o contrato HTTP com OpenAPI. `OrderController` é a implementação e concentra os mapeamentos das rotas. Serviços coordenam os casos de uso; repositories persistem entidades; o pacote `messaging` contém publicação, consumer e recuperação de mensagens, enquanto a topologia RabbitMQ fica centralizada em `config`.

## Camadas

| Pacote | Responsabilidade |
|---|---|
| `controller` | Rotas REST e contrato documentado |
| `service` | Criação, consulta e transições de processamento |
| `repository` | Acesso a pedidos e eventos Outbox |
| `model` | Entidades persistidas e status |
| `dto` | Contratos HTTP e mensagem RabbitMQ |
| `messaging` | Topologia, publicação Outbox, consumo, retry e DLQ |
| `exception` | Erros HTTP padronizados |
| `config` | Configurações centralizadas de RabbitMQ, OpenAPI/Swagger e agendamento |

Erros usam `ProblemDetail` do Spring, compatível com RFC 9457. `fieldErrors` e `timestamp` são extensões documentadas para a API.
