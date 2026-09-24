# calisat-ms-carrito

> Microservicio Spring Boot del carrito de compras: un carrito por usuario autenticado, con validación de SKU/stock contra catálogo e inventario.

![Versión](https://img.shields.io/badge/version-2.0.0-2563EB)
![Java](https://img.shields.io/badge/Java-21-F89820?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![Estado](https://img.shields.io/badge/estado-modo%20acad%C3%A9mico-FACC15)

**Versión actual: `2.0.0`** (definida en `pom.xml` · historial en [`CHANGELOG.md`](CHANGELOG.md))

---

## 📑 Índice

- [📋 Descripción general](#-descripción-general)
- [✨ Características principales](#-características-principales)
- [🏗️ Arquitectura](#-arquitectura)
- [🚀 Requisitos](#-requisitos)
- [⚙️ Configuración](#-configuración)
- [▶️ Ejecución local](#-ejecución-local)
- [📡 Endpoints principales](#-endpoints-principales)
- [🗃️ Modelo de datos](#-modelo-de-datos)
- [🔒 Seguridad](#-seguridad)
- [🧪 Tests](#-tests)
- [📦 Despliegue](#-despliegue)
- [🔗 Microservicios relacionados](#-microservicios-relacionados)
- [📄 Licencia y modo académico](#-licencia-y-modo-académico)

---

## 📋 Descripción general

**calisat-ms-carrito** gestiona el **carrito de compras** de la plataforma Calisat. Cada usuario autenticado tiene **un único carrito** (scopeado por el `sub` del JWT) que se crea perezosamente en estado `ABIERTO` al primer `GET`.

Desde la **v2.0.0** (fase B de integración entre microservicios), el alta de items valida el SKU contra **calisat-ms-catalogo** (producto activo + *snapshot* de precio) y la disponibilidad contra **calisat-ms-inventario** (409 si no hay stock), con **degradación elegiente**: si un servicio dependiente está caído, la validación se omite y el alta continúa.

Expone OpenAPI 3 + Swagger UI y se consume desde `calisat-ms-orden` al confirmar el checkout.

## ✨ Características principales

- 🛒 **Carrito único por usuario** con *upsert* perezosos (`GET` crea el carrito vacío si no existe).
- ➕ **Items con acumulación**: agregar un SKU ya presente **suma** la cantidad (upsert por `(carrito, sku)`).
- ✅ **Validación integrada (v2)**: SKU activo en catálogo (404 `SkuNoEncontradoException`) y disponibilidad en inventario (409 `StockInsuficienteException`).
- 💲 **Snapshot de precio** (`precio_unitario_visto`) capturado al agregar el item.
- 🔄 **Degradación elegiente**: caída de catálogo/inventario no bloquea el alta (try/catch best-effort).
- 🧾 **Validación de carrito** con advertencias simples (vacío, total de unidades, precios sin verificar).
- 🧹 **Vaciar carrito** idempotente (`204` siempre).
- 📕 **OpenAPI 3 + Swagger UI** (springdoc 3.1.0) anotados.
- 🩺 **Actuator** + **Docker multi-stage** con health check.
- 🧪 **23 tests** (servicio + clientes HTTP mockeados).

## 🏗️ Arquitectura

```mermaid
flowchart LR
    F[calisat-frontend] -->|JWT| CAR[calisat-ms-carrito<br/>:8084]
    ORD[calisat-ms-orden] -->|GET carrito al checkout| CAR
    CAR -->|GET /api/v1/catalogo/{sku}| CAT[calisat-ms-catalogo :8082]
    CAR -->|GET /api/v1/stock/sku/{sku}| INV[calisat-ms-inventario :8083]
    CAR --> PG[(PostgreSQL<br/>calisat_carrito)]
```

### Estructura de paquetes

```
com.califorge.mscarrito
├── client/        # CatalogoClient, InventarioClient (RestTemplate)
├── config/        # SecurityConfig, RestTemplateConfig, CORS
├── controller/    # CarritoController
├── dto/           # CarritoRequest/Response, CarritoItem*, ...
├── exception/     # GlobalExceptionHandler, SkuNoEncontrado, StockInsuficiente, ...
├── model/         # Carrito, CarritoItem, EstadoCarrito (JPA)
├── repository/    # CarritoRepository, CarritoItemRepository
└── service/       # CarritoService (@Transactional)
```

## 🚀 Requisitos

| Requisito | Versión mínima |
|-----------|----------------|
| JDK | **21+** (enforcer) |
| Maven | 3.6.3+ (o wrapper `./mvnw`) |
| Docker + Docker Compose | 24+ |
| Servicios opcionales | `calisat-ms-catalogo` (:8082) y `calisat-ms-inventario` (:8083) para validación integrada |

## ⚙️ Configuración

Valores de `src/main/resources/application.yaml`, `docker-compose.yml` y variables de cliente:

| Parámetro | Valor |
|-----------|-------|
| **Puerto del servicio** | **`8084`** (`application.yaml` y mapeo Compose `8084:8080`; en contenedor la app escucha en `8080` vía `SERVER_PORT`) |
| Base de datos | PostgreSQL · `calisat_carrito` |
| Host de BD (local) | `localhost:5433` (Compose publica `5433:5432`) |
| Usuario / contraseña BD | `postgres` / `postgres` *(solo académico)* |
| `ddl-auto` | `update` |
| JWT *issuer* | `https://login.microsoftonline.com/e5372bf0-c5e3-4286-887c-79069f209c1f/v2.0` |
| JWT *audience* | `d221f0d2-1a7c-4872-ad6c-367a1f0717ec` |
| Rutas públicas | `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**` |

### Variables de integración (clientes)

| Variable | Defecto | Descripción |
|----------|---------|-------------|
| `CALISAT_CATALOGO_URL` | `http://localhost:8082` | Base URL de `calisat-ms-catalogo` |
| `CALISAT_INVENTARIO_URL` | `http://localhost:8083` | Base URL de `calisat-ms-inventario` |

> ⚠️ **Modo académico**: issuer, audience y credenciales están **hardcodeados**; en producción deben externalizarse.

## ▶️ Ejecución local

### 1. Base de datos

```bash
docker compose up -d postgres-db
```

Levanta PostgreSQL 15 publicado en `localhost:5433`.

### 2. Aplicación

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Para probar la validación integrada, levanta antes `calisat-ms-catalogo` (:8082) y `calisat-ms-inventario` (:8083).

### 3. Docker Compose

```bash
docker compose up --build
```

Servicio en `http://localhost:8084` (Swagger: `/swagger-ui.html`).

## 📡 Endpoints principales

Base: `http://localhost:8084/api/v1/carrito`

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/carrito` | Carrito propio (crea vacío `ABIERTO` si no existe) | JWT |
| `POST` | `/api/v1/carrito/items` | Agrega item por SKU (suma si ya existe; `201` + `Location`) | JWT |
| `PUT` | `/api/v1/carrito/items/{sku}` | Reemplaza la cantidad de un item (404 si no está) | JWT |
| `DELETE` | `/api/v1/carrito/items/{sku}` | Elimina un item (`204`; 404 si no existe) | JWT |
| `DELETE` | `/api/v1/carrito` | Vacía el carrito (idempotente, `204`) | JWT |
| `POST` | `/api/v1/carrito/validar` | Valida el carrito y devuelve advertencias | JWT |

**Total: 6 endpoints** · *Swagger UI*: `/swagger-ui.html`

### Ejemplo

```bash
# Ver carrito (lo crea vacío si no existe)
curl http://localhost:8084/api/v1/carrito -H "Authorization: Bearer $TOKEN"

# Agregar 2 unidades
curl -X POST http://localhost:8084/api/v1/carrito/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sku":"BARRAS-001","cantidad":2}'
```

### Errores de integración

| Código | Excepción | Condición |
|--------|-----------|-----------|
| `404` | `SkuNoEncontradoException` | SKU inexistente o inactivo en catálogo |
| `409` | `StockInsuficienteException` | Cantidad supera el stock disponible en inventario |
| `400` | `CantidadInvalidaException` | Cantidad `< 1` o entrada inválida |
| `404` | `ItemCarritoNoEncontradoException` | SKU no presente en el carrito |

## 🗃️ Modelo de datos

### Entidad `Carrito` (tabla `carrito`)

| Campo | Tipo | Restricciones |
|-------|------|---------------|
| `id` | `UUID` | PK |
| `usuario_sub` | `String(255)` | **Único**, `NOT NULL` — `sub` del JWT (1 carrito por usuario) |
| `estado` | `Enum` | `ABIERTO` (default) · `CERRADO` |
| `items` | `1..N` | `@OneToMany LAZY` |
| `fecha_creacion` / `fecha_actualizacion` | `LocalDateTime` | `@PrePersist` / `@PreUpdate` |

### Entidad `CarritoItem` (tabla `carrito_item`)

| Campo | Tipo | Restricciones |
|-------|------|---------------|
| `id` | `UUID` | PK |
| `carrito_id` | FK → `carrito` | `NOT NULL` |
| `sku` | `String(64)` | `NOT NULL` · **único por carrito** (`uk_carrito_item_sku`) |
| `cantidad` | `Integer` | `NOT NULL`, `≥ 1` |
| `precio_unitario_visto` | `BigDecimal(10,2)` | Snapshot de precio al agregar |
| `fecha_agregado` | `LocalDateTime` | `@PrePersist` |

## 🔒 Seguridad

- **JWT (OAuth2 Resource Server)** de **Microsoft Entra ID**: validación de *issuer* + *audience*.
- **Sin RBAC**: cualquier usuario autenticado gestiona **su propio** carrito; el *scope* se resuelve con `jwt.getSubject()` (*modo académico, usuario genérico*).
- **Rutas públicas**: `/actuator/health` y Swagger UI; el resto exige token.
- **CSRF deshabilitado** · **CORS** restringido al origen del despliegue.

## 🧪 Tests

```bash
./mvnw test
```

| Suite | Archivos | Tests |
|-------|----------|-------|
| Unitarios | `CarritoServiceTest` (17), `CatalogoClientTest` (3), `InventarioClientTest` (3) | **23** |

Los tests de clientes usan `RestTemplate` mockeado (sin red).

## 📦 Despliegue

### Docker

```bash
docker build -t calisat-ms-carrito:2.0.0 .
docker run -p 8084:8080 --name calisat-ms-carrito calisat-ms-carrito:2.0.0
```

**Dockerfile multi-stage:**

1. `maven` (Temurin 21) → `mvn clean package`.
2. `eclipse-temurin:21-jre-alpine` → JAR con usuario no root, `MaxRAMPercentage=75`, `HEALTHCHECK` en `/actuator/health`.

### Docker Compose

```bash
docker compose up --build
```

Levanta PostgreSQL 15 (`calisat_carrito`, puerto host `5433`) + app en **8084**, red `calisat-net`.

## 🔗 Microservicios relacionados

| Repositorio | Relación |
|-------------|----------|
| [calisat-ms-catalogo](https://github.com/DavNat13/calisat-ms-catalogo) | **Dependencia**: valida SKU/precio vía `CatalogoClient` (`CALISAT_CATALOGO_URL`, defecto `:8082`) |
| [calisat-ms-inventario](https://github.com/DavNat13/calisat-ms-inventario) | **Dependencia**: valida stock vía `InventarioClient` (`CALISAT_INVENTARIO_URL`, defecto `:8083`) |
| [calisat-ms-orden](https://github.com/DavNat13/calisat-ms-orden) | **Consumidor**: lee y vacía el carrito al crear la orden (`CALISAT_CARRITO_URL`) |
| [calisat-ms-notificaciones](https://github.com/DavNat13/calisat-ms-notificaciones) | Consulta carritos abiertos para el cron de abandono (`CALISAT_CARRITO_URL`) |
| [calisat-ms-envios](https://github.com/DavNat13/calisat-ms-envios) | Envíos y seguimiento (puerto 8086) |
| [calisat-frontend](https://github.com/DavNat13/calisat-frontend) | SPA React 19 (v1.4.0) — ruta `/carrito` |

## 📄 Licencia y modo académico

Proyecto desarrollado en **modo académico**; sin licencia open source formal. Issuer, audience y credenciales están *hardcodeados* con fines educativos; sin RBAC (usuario genérico autenticado).

- **Versión actual**: `2.0.0` — *breaking change*: `CarritoService` ahora inyecta `CatalogoClient` e `InventarioClient`
- **Historial de cambios**: [`CHANGELOG.md`](CHANGELOG.md)
