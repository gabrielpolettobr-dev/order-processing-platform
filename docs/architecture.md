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

`OrderApi` documenta o contrato HTTP com OpenAPI. `OrderController` é a implementação e concentra os mapeamentos das rotas. Serviços coordenam os casos de uso; repositories persistem entidades. O pacote `messaging` separa `producer`, `consumer` e `dto`; a topologia RabbitMQ permanece em `config`. `OrderMapper` usa MapStruct para converter entidades e DTOs da API, mantendo cálculos e regras de negócio nas entidades.

## Camadas

| Pacote | Responsabilidade |
|---|---|
| `controller` | Rotas REST e contrato documentado |
| `service` | Criação, consulta e transições de processamento |
| `repository` | Acesso a pedidos e eventos Outbox |
| `model.entity` / `model.enums` | Entidades JPA e enums do domínio, separados por tipo |
| `dto.request` / `dto.response` | Contratos HTTP |
| `messaging.producer` / `messaging.consumer` / `messaging.dto` | Publicação e reserva Outbox; consumo, retry e recuperação; contrato de mensagem RabbitMQ |
| `mapper` | Conversão entre entidades e DTOs com MapStruct |
| `exception` | Erros HTTP padronizados |
| `config` | Configuração centralizada de Jackson, RabbitMQ, OpenAPI/Swagger e agendamento |

Erros usam `ProblemDetail` do Spring, compatível com RFC 9457. `fieldErrors` e `timestamp` são extensões documentadas para a API.
