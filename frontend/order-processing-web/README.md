# OrderFlow — Frontend

Interface Angular para cadastrar, consultar e acompanhar pedidos da plataforma.

## Executar localmente

Com o backend disponível em `http://localhost:8080`, execute:

```powershell
npm install
npm start
```

A aplicação estará em `http://localhost:4200`. O proxy configurado em `proxy.conf.json` encaminha `/api` ao backend local, evitando configuração de CORS durante o desenvolvimento.

## Funcionalidades

- Listagem paginada, ordenação e filtros por status e período de criação.
- Atualização manual e polling a cada 3 segundos enquanto existirem pedidos em `RECEIVED` ou `PROCESSING`.
- Formulário com múltiplos itens, validações e cálculo informativo de subtotal/total. O total definitivo é sempre calculado pelo backend.
- Detalhes do pedido com itens, valores, datas e acompanhamento visual das etapas.
- Mensagens de carregamento, sucesso e erro.

## Organização

- `core/models`: contratos de pedido, itens, filtros, página e status.
- `features/orders/services`: comunicação HTTP reutilizável com `/api/orders`.
- `features/orders/components`: telas standalone de listagem, formulário, detalhes e badge de status.
- `app.routes.ts`: rotas lazy-loaded da aplicação.

O acesso à API usa caminhos relativos (`/api`), de modo que em produção o servidor web do frontend pode encaminhar essas chamadas ao backend na mesma origem.

## Validar

```powershell
npm test -- --watch=false
npm run build
```
