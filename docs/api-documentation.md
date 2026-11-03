# API de pedidos

Base URL local: `http://localhost:8080`. A especificação OpenAPI e a interface Swagger ficam em `/api-docs` e `/swagger-ui.html`.

## Criar pedido

`POST /api/orders`

O backend calcula subtotais e total; não há campo de total no contrato de entrada.

```json
{
  "customerName": "Maria Silva",
  "customerEmail": "maria@example.com",
  "items": [
    {
      "description": "Teclado",
      "quantity": 2,
      "unitPrice": 150.00
    },
    {
      "description": "Mouse",
      "quantity": 1,
      "unitPrice": 80.00
    }
  ]
}
```

Retorna `202 Accepted`, `Location: /api/orders/{id}` e o pedido criado em `RECEIVED`. Campos obrigatórios: nome, e-mail válido e pelo menos um item. Quantidade deve estar entre `1` e `1.000.000`; preço unitário deve ser maior que zero e ter até duas casas decimais. Subtotais e total que excedam a precisão `NUMERIC(19,2)` são rejeitados.

## Listar pedidos

`GET /api/orders?page=0&size=20&sort=createdAt,desc`

Filtros opcionais:

| Parâmetro | Descrição |
|---|---|
| `status` | `RECEIVED`, `PROCESSING`, `COMPLETED` ou `FAILED` |
| `createdFrom` | Data/hora inclusiva em ISO-8601, por exemplo `2026-11-02T00:00:00Z` |
| `createdTo` | Data/hora inclusiva em ISO-8601 |
| `page` | Página baseada em zero; padrão `0` |
| `size` | Itens por página; padrão `20` |
| `sort` | Campo e direção padrão `createdAt,desc`; aceita a sintaxe Spring Data |

O tamanho máximo de página padrão é `100` e pode ser configurado por `ORDERS_MAX_PAGE_SIZE`. Tamanhos acima do limite e campos de ordenação fora da lista permitida (`id`, `createdAt`, `updatedAt`, `customerName`, `customerEmail`, `totalAmount`, `status`) retornam `400 Bad Request`. Período com `createdFrom` posterior a `createdTo` também retorna `400 Bad Request`.

## Consultar pedido

`GET /api/orders/{id}` retorna os detalhes do pedido, incluindo itens, subtotal, total, status e timestamps. UUID inexistente retorna `404 Not Found`.

## Erros

Erros usam o formato **Problem Details for HTTP APIs** (RFC 9457), com content type `application/problem+json` e propriedades `type`, `title`, `status`, `detail` e `instance`. A API inclui `timestamp` e `errorId` como extensões; o `errorId` também é retornado no header `X-Error-Id` para correlacionar a resposta com os logs, sem expor identificadores de negócio ou detalhes internos. Para erros de validação, `fieldErrors` contém campos públicos e mensagens padronizadas, sem repetir os valores enviados ou nomes internos de métodos/parâmetros. Respostas derivadas de erros do framework também usam detalhes controlados pela API; exceções inesperadas retornam mensagem genérica e são registradas nos logs. Entrada inválida, consulta inválida e violações das regras de valor do pedido retornam `400`; pedido inexistente retorna `404`.
