# Curpify — API de Validación de CURP en Milisegundos

Curpify es una API ultra rápida y confiable para validar CURP en México.  
Diseñada para desarrolladores y empresas que necesitan validación inmediata para onboarding, KYC, formularios, procesos legales o automatizaciones internas.

## Autenticación (API Key)

Curpify usa API Keys vía header:

- Header: `x-api-key: curp_...`

### Modos de uso
- **Demo (sin key):** permite validar con límite **5 por día por IP**.
- **Con API Key:** aplica rate-limit por plan (mensual) y habilita métricas/dashboard.

## Rate limits

Curpify aplica límites por plan:

- Demo (sin API key): **5 requests por día** (por IP)
- Free: **50 requests por mes**
- Developer: **5,000 requests por mes**
- Business: **50,000 requests por mes**

### Tabla

| Plan | Límite |
|------|--------|
| Demo | 5 validaciones por día por IP |
| Free | 50 validaciones por mes por API key |
| Developer | 5,000 validaciones por mes por API key |
| Business | 50,000 validaciones por mes por API key |

> Si necesitas más volumen, contáctanos para plan a la medida.

## Errores típicos

### 401 Unauthorized
Falta API key (cuando el modo demo no aplica al endpoint):
```json
{ "ok": false, "error": "Falta header x-api-key" }

### 400 Bad Request
Request inválido (payload mal formado o faltan campos):
```json
{ "ok": false, "error": "Request inválido" }`
```
### Endpoints
https://curp-api-production.up.railway.app/api/curp/validate

POST /api/curp/validate

Header: x-api-key: curp_...

Body:
```json
{ "curp": "GODE561231HDFABC09" }
```