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

## Base URL
- Local: http://localhost:3000
- Producción: /api/curp/validate

# Curpify

## ¿Qué es Curpify?

Curpify es una **API de validación de CURP**, diseñada para integrarse fácilmente en aplicaciones, sistemas y flujos backend que requieren validar información de identidad de manera rápida y confiable.

Forma parte del ecosistema **Evilink**, siguiendo un enfoque **developers first** y contratos claros para su consumo vía API.

---

## Versiones de Curpify

Curpify se ofrece en **dos modalidades principales**, cada una con un propósito distinto.

---

## Curpify Lite

**Curpify Lite** es una versión **limitada y simplificada** del servicio, pensada exclusivamente para pruebas, prototipos y proyectos pequeños.

### Características principales

- Precio: **4.99 USD**
- Límite: **hasta 100 validaciones por día**
- Acceso únicamente a funcionalidades básicas de validación
- Sin capacidades avanzadas ni configuraciones extendidas

---

### Canales donde está disponible

Curpify Lite **solo puede obtenerse** a través de los siguientes canales:

- **RapidAPI**
- **Postman (Postman Community / API Network)**

No está disponible directamente desde el portal principal de Evilink ni mediante planes empresariales.

---

### Casos de uso de Curpify Lite

- Pruebas rápidas de integración
- Prototipos
- Proyectos personales
- Evaluación del servicio antes de migrar a una versión completa

---

### Qué Curpify Lite NO es

Curpify Lite **NO** está diseñado para:

- Uso en producción a gran escala
- Altos volúmenes de tráfico
- Casos empresariales
- Integraciones críticas

Para esos escenarios se requiere la versión completa de Curpify.

---

## Curpify (versión completa)

La versión completa de Curpify está pensada para:

- Entornos productivos
- Mayor volumen de validaciones
- Control avanzado de límites
- Integraciones empresariales
- Uso directo mediante Evilink

Esta versión no se distribuye a través de RapidAPI ni Postman Community.

---

## Principios técnicos

Curpify sigue los principios del ecosistema Evilink:

- APIs contract-first (OpenAPI)
- Seguridad desde el MVP
- Separación clara entre versiones
- Optimización de costos sin sacrificar robustez
- Escalabilidad progresiva

---

## Relación con Evilink y Nexus

- Curpify es un **producto independiente** dentro del ecosistema Evilink.
- Nexus utiliza la documentación oficial de Curpify para responder preguntas sobre:
  - versiones
  - límites
  - disponibilidad
  - casos de uso

Nexus no debe presentar Curpify Lite como una solución empresarial ni como la versión principal del producto.

---

## Regla importante

Si un usuario requiere:
- mayor volumen
- uso productivo
- garantías operativas

Debe migrar a la **versión completa de Curpify** y no utilizar Curpify Lite.
