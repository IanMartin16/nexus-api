# CryptoLink — API de precios cripto (evi_link)

## Autenticación (API Key)
La mayoría de endpoints requieren API key vía header:

- Header: `x-api-key: ...`

## Endpoints públicos

### Salud / metadata
- `GET /v1/ping`
- `GET /v1/meta`

### Catálogos
- `GET /v1/symbols`
- `GET /v1/fiats`

### Plan / límites del API key actual
- `GET /v1/me` (requiere `x-api-key`)

### Precios
- `GET /v1/price?symbol=BTC&fiat=USD` (requiere `x-api-key`)
- `GET /v1/prices?symbols=BTC,ETH,SOL&fiat=EUR` (requiere `x-api-key`)

## Streaming (SSE)

### Token para SSE
- `GET /v1/auth/sse-token` (genera token para SSE)

### Stream
- `GET /v1/stream/prices?token=...&symbols=BTC,ETH&fiat=USD`

Notas:
- SSE puede requerir `token` o `x-api-key`.
- Recomendado: usar `token`.

## Admin (doble candado)
Estos endpoints requieren DOS headers:

- `x-admin-secret: ...`
- `x-master-admin: ...`

Endpoints:
- `POST /admin/v1/keys` (crear key)
- `POST /admin/v1/keys/{apiKey}/revoke`
- `POST /admin/v1/keys/{apiKey}/plan`
- `POST /admin/v1/keys/{apiKey}/expires`
