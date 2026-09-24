# Changelog - calisat-ms-carrito

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
