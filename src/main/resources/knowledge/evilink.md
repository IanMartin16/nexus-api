# Evilink

## ¿Qué es Evilink?

Evilink es un **ecosistema de APIs y servicios digitales**, diseñado bajo un enfoque **developers first**, cuyo objetivo es ofrecer herramientas técnicas especializadas que pueden consumirse de forma independiente o como parte de un conjunto integrado.

Cada producto dentro de Evilink tiene **identidad propia**, **propósito específico** y **stack adecuado a su dominio**, pero todos comparten una **arquitectura común**, principios técnicos definidos y una visión de escalabilidad y robustez.

---

## Qué Evilink NO es

Evilink **NO** es:

- Una plataforma IoT
- Un sistema de automatización de dispositivos físicos
- Una solución de domótica
- Un SaaS genérico para usuarios finales
- Una plataforma que conecta sensores, cámaras, cerraduras o hardware físico

Evilink se enfoca exclusivamente en **software, APIs y servicios digitales**.

---

## Enfoque del ecosistema

Evilink sigue un enfoque **developers first**.

Está pensado principalmente para:
- Desarrolladores
- Arquitectos de software
- Equipos técnicos
- Proyectos que requieren APIs confiables y bien definidas

Algunos productos pueden ofrecer **capas simples o interfaces visuales** para usuarios no técnicos, pero **ese no es el foco principal del ecosistema**.

---

## Estructura del ecosistema Evilink

Evilink agrupa múltiples productos bajo una misma visión técnica.

Ejemplos de productos dentro del ecosistema:

- **Curpify**  
  API enfocada en validación y servicios relacionados con CURP, diseñada para integrarse fácilmente en sistemas existentes.

- **CryptoLink**  
  API orientada a datos financieros y de criptomonedas, con flujos de mayor carga y procesamiento.

- **Nexus**  
  Asistente y capa de conocimiento del ecosistema Evilink. Nexus responde basándose exclusivamente en la documentación oficial del ecosistema.

Otros productos pueden añadirse en el futuro siguiendo los mismos principios.

---

## Principios técnicos comunes

Todos los productos de Evilink comparten los siguientes principios:

- Contratos claros (OpenAPI / Swagger)
- Separación de responsabilidades
- Seguridad desde el MVP
- Escalabilidad progresiva
- Optimización de costos en etapas tempranas
- Elección de stack según el peso real del flujo

---

## Arquitectura general (Ingesta de Nexus)

Evilink utiliza una arquitectura por capas:

- **Capa de presentación e ingesta**  
  Usada para interfaces, documentación, dashboards y gateways ligeros.

- **Capa de servicios core**  
  Donde vive la lógica de negocio, procesamiento pesado, análisis y flujos complejos.

- **Workers y pipelines**  
  Para trabajos asíncronos, ingesta de datos, normalización y análisis.

Nexus actúa como **capa de ingesta y conocimiento**, no como motor de procesamiento pesado.

---

## Rol de Nexus dentro de Evilink

Nexus es el **asistente del ecosistema Evilink**.

Sus respuestas se basan en:
- Documentación oficial del ecosistema
- READMEs de productos
- Principios arquitectónicos definidos

Nexus **no inventa información**, **no asume dominios externos** y **no representa a Evilink como una plataforma IoT**.

---

## Regla fundamental del ecosistema

Si un nuevo producto, idea o propuesta:

- No encaja en estos principios
- Contradice esta definición
- Introduce ambigüedad sobre el dominio de Evilink

Entonces **debe replantearse antes de implementarse**.

Este documento es la **fuente de verdad** del ecosistema Evilink.
