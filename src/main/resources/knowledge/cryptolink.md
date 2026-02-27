# CryptoLink

## ¿Qué es CryptoLink?

CryptoLink es una **API de precios y datos de criptomonedas**, diseñada para ofrecer información confiable en tiempo real mediante endpoints REST y streaming (SSE).

Forma parte del ecosistema **Evilink** y está orientada a desarrolladores que requieren datos cripto actualizados para aplicaciones, dashboards, análisis o integraciones backend.

---

## Autenticación (API Key)

La mayoría de los endpoints requieren una API Key enviada mediante header:

- Header: `x-api-key: <API_KEY>`

---

## Endpoints públicos

### Salud y metadata
- `GET /v1/ping`
- `GET /v1/meta`

---

### Catálogos
- `GET /v1/symbols`
- `GET /v1/fiats`

---

### Información del plan actual
- `GET /v1/me`  
  *(requiere `x-api-key`)*

Devuelve información sobre:
- límites
- plan asociado
- estado del API key

---

## Precios

- `GET /v1/price?symbol=BTC&fiat=USD`  
- `GET /v1/prices?symbols=BTC,ETH,SOL&fiat=EUR`  

*(requieren `x-api-key`)*

---

## Streaming de precios (SSE)

CryptoLink soporta **Server-Sent Events (SSE)** para recibir precios en tiempo real.

### Token para SSE
- `GET /v1/auth/sse-token`

Genera un token temporal para autenticarse en el stream.

---

### Stream de precios
- `GET /v1/stream/prices?token=...&symbols=BTC,ETH&fiat=USD`

Notas importantes:
- El stream puede autenticarse mediante `token` o `x-api-key`
- **Se recomienda usar `token`**
- Diseñado para consumo en tiempo real

---

## Endpoints administrativos (doble candado)

Estos endpoints están protegidos mediante **dos headers obligatorios**:

- `x-admin-secret`
- `x-master-admin`

### Endpoints disponibles
- `POST /admin/v1/keys` — crear API key
- `POST /admin/v1/keys/{apiKey}/revoke`
- `POST /admin/v1/keys/{apiKey}/plan`
- `POST /admin/v1/keys/{apiKey}/expires`

Estos endpoints no están disponibles para usuarios finales.

---

## Versiones del producto

Actualmente **CryptoLink no cuenta con versión Lite**.

Toda la funcionalidad disponible corresponde a la **versión completa del servicio**.

Una posible versión Lite podrá existir en el futuro, pero **no está disponible en este momento**.

---

## Qué CryptoLink NO es

CryptoLink **NO** es:
- Una plataforma de trading
- Un exchange
- Un sistema de señales de compra/venta
- Un asesor financiero
- Un producto de análisis social o de tendencias

CryptoLink se enfoca exclusivamente en **datos cripto y precios**.

---

## Relación con Evilink y Nexus

- CryptoLink es un **producto independiente** dentro del ecosistema Evilink.
- Nexus utiliza esta documentación para responder preguntas sobre:
  - endpoints
  - autenticación
  - límites
  - streaming
  - alcance del producto

Nexus no debe presentar a CryptoLink como un producto de trading ni de análisis social.

---

## Roadmap (alto nivel)

En el ecosistema Evilink existen planes para desarrollar **productos complementarios**, como análisis de tendencias y señales (por ejemplo, Social_Link).

Estos productos **no forman parte actualmente de CryptoLink** y se documentarán de manera independiente cuando estén disponibles.

---

## Principios técnicos

CryptoLink sigue los principios del ecosistema Evilink:

- APIs contract-first
- Seguridad desde el MVP
- Separación clara de responsabilidades
- Optimización de costos sin sacrificar robustez
- Escalabilidad progresiva
