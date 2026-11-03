# Order Processing Platform

Plataforma de criação e processamento assíncrono de pedidos. Nesta etapa, o backend Spring Boot, PostgreSQL e RabbitMQ estão implementados; a interface Angular será adicionada em uma etapa posterior.

## Executar o backend com Docker

Pré-requisito: Docker Desktop com Docker Compose. Na raiz do repositório, execute:

```powershell
docker compose up --build
```

O Compose inicia PostgreSQL, RabbitMQ e API, aguarda os health checks das dependências e executa as migrations Flyway automaticamente. Para interromper, use `Ctrl+C` ou `docker compose down`. Para iniciar novamente, execute `docker compose up --build`. Os dados do PostgreSQL e do RabbitMQ permanecem nos volumes nomeados; `docker compose down -v` os remove.

| Serviço | URL/porta local | Credenciais padrão |
|---|---|---|
| API | `http://localhost:8080` | — |
| Swagger UI | `http://localhost:8080/swagger-ui.html` | — |
| OpenAPI JSON | `http://localhost:8080/api-docs` | — |
| Actuator health | `http://localhost:8080/actuator/health` | — |
| PostgreSQL | `localhost:5432` | `orders_user` / `orders_dev_password` |
| RabbitMQ AMQP | `localhost:5672` | `orders_user` / `rabbit_dev_password` |
| RabbitMQ Management | `http://localhost:15672` | `orders_user` / `rabbit_dev_password` |

As credenciais e parâmetros podem ser alterados pelas variáveis de ambiente do Compose. O arquivo `.env.example` contém as variáveis de banco, RabbitMQ e processamento; `OUTBOX_CLAIM_TIMEOUT` controla por quanto tempo um evento fica reservado antes de poder ser recuperado por outra execução. Valores padrão de desenvolvimento permitem iniciar sem criar um `.env`.

## Executar testes do backend

Com Java 21 instalado, execute na pasta `backend/order-processing-api`:

```powershell
.\mvnw.cmd test
```

Para criar o artefato e executar todas as verificações Maven:

```powershell
.\mvnw.cmd clean verify
```

Os testes unitários cobrem cálculo dos valores, criação, limites de listagem, falha/retry e publicação da Outbox. A suíte `OrderProcessingIntegrationTest` usa Testcontainers para testar migrations PostgreSQL, fluxo API→Outbox→RabbitMQ→consumer, DLQ, concorrência de processamento, disputa por eventos da Outbox, filtros/paginação e o schema de erro exposto pelo OpenAPI. É necessário que o Docker esteja ativo para executar esses testes; sem daemon, o JUnit os marca como ignorados. A aplicação em container é construída com Java 21 e não exige JDK ou Maven instalado localmente para execução via Compose.

Para exercitar a falha simulada via API, configure `ORDER_PROCESSING_FAIL_ITEM_DESCRIPTION_PREFIX` com um prefixo e crie um item cuja descrição comece com ele. O padrão vazio desativa essa simulação; ela serve para validar retry e DLQ, não representa uma regra de negócio permanente.

## API

- `POST /api/orders` — cria pedido e retorna `202 Accepted`; o processamento ocorre em background.
- `GET /api/orders` — lista com paginação e filtros opcionais `status`, `createdFrom` e `createdTo`.
- `GET /api/orders/{id}` — consulta pedido, itens, valores e status.

Exemplos e formato de erros: [docs/api-documentation.md](docs/api-documentation.md).

## Arquitetura do backend

O backend segue camadas: `controller` (rotas HTTP e contrato `OrderApi`), `service` (casos de uso), `repository` (persistência), `model` (entidades e status), além de `dto`, `exception`, `messaging` e `config`. A interface `OrderApi` documenta os contratos com OpenAPI e é implementada por `OrderController`; os caminhos e mapeamentos HTTP ficam no controller. Erros seguem o padrão RFC 9457 com `ProblemDetail` e são descritos no OpenAPI como `application/problem+json`.

A criação persiste pedido, itens e evento Outbox na mesma transação. Um publicador separado envia o evento ao RabbitMQ; o consumidor atualiza o status e ignora entregas duplicadas de pedidos em processamento recente ou já finalizados. Após as tentativas configuradas, a mensagem é roteada para `orders.dlq` e o pedido é marcado como `FAILED`. A topologia RabbitMQ é declarada pela aplicação. Detalhes e limitações: [docs/technical-decisions.md](docs/technical-decisions.md).

Diagramas: [arquitetura](docs/architecture.md), [fluxo de processamento](docs/processing-flow.md) e [modelo do banco](docs/database-model.md).