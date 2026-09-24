# Changelog - calisat-ms-carrito

## [2.0.0] - 2026-09-23

### BREAKING CHANGE
- Versión pom.xml incrementada a 2.0.0 (fase B: integración entre microservicios)
- `CarritoService` ahora exige en su constructor los nuevos clientes HTTP `CatalogoClient` e `InventarioClient` (paquete `com.califorge.mscarrito.client`); cualquier construcción manual del servicio debe inyectarlos
- El alta de items pasa a validar el SKU contra catálogo e inventario: un SKU inexistente/inactivo devuelve 404 (`SkuNoEncontradoException`) y una cantidad que supera la disponibilidad devuelve 409 (`StockInsuficienteException`); ambos son nuevos códigos de error del API

### Added
- Paquete `client` con clientes RestTemplate aislados: `CatalogoClient` (GET /api/v1/catalogo/{sku}) e `InventarioClient` (GET /api/v1/stock/sku/{sku}), sin service discovery
- URLs base por variable de entorno con default localhost: `CALISAT_CATALOGO_URL` (http://localhost:8082) y `CALISAT_INVENTARIO_URL` (http://localhost:8083)
- `RestTemplateConfig` con el bean `RestTemplate` compartido por los clientes
- Validación de SKU activo en catálogo al agregar item, con snapshot de precio visto (`precio_unitario_visto`)
- Validación de disponibilidad en inventario al agregar item (409 si no alcanza el stock)
- Revalidación de precio/stock en `POST /api/v1/carrito/validar` (solo advertencias, nunca interrumpe el flujo)
- Degradación elegante: si catálogo/inventario están caídos, la validación se omite y el alta del item continúa (try/catch, jamás rompe el flujo principal)
- Manejadores en `GlobalExceptionHandler` para `SkuNoEncontradoException` (404) y `StockInsuficienteException` (409)
- Tests de clientes con `RestTemplate` mockeado (`CatalogoClientTest`, `InventarioClientTest`) y tests de servicio para validación snapshot/degradación

## [1.3.0] - 2026-09-23

### Added
- Microservicio calisat-ms-carrito con Spring Boot 4.1.0 y Java 21 (puerto 8084)
- Docker Compose con PostgreSQL 15 (`calisat_carrito`, puerto host 5433) y app Spring Boot
- Entidades JPA Carrito (1 carrito ABIERTO por usuario) y CarritoItem con UNIQUE(carrito_id, sku) para upsert
- CarritoService con obtener, agregar/upsert de items, cambiar cantidad, quitar item, vaciar y validar
- Endpoints: GET/DELETE /api/v1/carrito, POST /api/v1/carrito/items, PUT/DELETE /api/v1/carrito/items/{sku}, POST /api/v1/carrito/validar
- SecurityConfig con validacion JWT de Azure Entra ID (issuer + audience) y CORS; sin RBAC (solo autenticacion)
- GlobalExceptionHandler con manejo de errores de negocio y validacion
- Tests de servicio (CarritoServiceTest)
- Health check via Spring Actuator
