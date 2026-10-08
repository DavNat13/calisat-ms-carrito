package com.califorge.mscarrito.controller;

import com.califorge.mscarrito.dto.CarritoItemRequest;
import com.califorge.mscarrito.dto.CarritoItemResponse;
import com.califorge.mscarrito.dto.CarritoItemUpdateRequest;
import com.califorge.mscarrito.dto.CarritoResponse;
import com.califorge.mscarrito.model.CarritoItem;
import com.califorge.mscarrito.service.CarritoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/carrito")
@Tag(name = "Carrito de compras", description = "Carrito del usuario autenticado. Sin RBAC: cualquier usuario autenticado gestiona su propio carrito, scopeado por el sub del JWT.")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    /**
     * Sub (propietario) sobre el que operar.
     *
     * <ul>
     *   <li>Con JWT: el sub del token (el cliente del panel/tienda).</li>
     *   <li>Sin JWT pero con rol SERVICIO (llamada MS->MS con
     *       X-Service-Token): el sub viene en el query param
     *       {@code usuarioSub}, que es lo que envian ms-orden al vaciar el
     *       carrito y ms-notificaciones al buscar carritos abandonados.</li>
     * </ul>
     */
    private String subPropietario(Jwt jwt, String usuarioSub) {
        if (jwt != null) {
            return jwt.getSubject();
        }
        if (usuarioSub != null && !usuarioSub.isBlank()) {
            return usuarioSub.trim();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Se requiere el parametro 'usuarioSub' cuando no hay JWT (llamada de servicio).");
    }

    /**
     * GET /api/v1/carrito
     * Devuelve el carrito propio del usuario autenticado (sub del JWT).
     * Crea el carrito vacio si aun no existe (upsert lazy). Requiere JWT
     * o, para llamadas MS->MS, el rol SERVICIO + el param usuarioSub.
     */
    @Operation(summary = "Obtener carrito propio",
            description = "Devuelve el carrito del usuario autenticado (sub del JWT). Si no existe se crea vacio con estado ABIERTO. Requiere JWT; sin roles. Las llamadas MS->MS (rol SERVICIO) pasan el propietario en el query param usuarioSub.")
    @GetMapping
    public ResponseEntity<CarritoResponse> obtenerCarrito(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String usuarioSub,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(carritoService.obtenerPorUsuario(subPropietario(jwt, usuarioSub)));
    }

    /**
     * POST /api/v1/carrito/items
     * Agrega un item al carrito; si el SKU ya existe suma la cantidad (upsert).
     * Devuelve 201 con Location al recurso del item. Requiere JWT.
     */
    @Operation(summary = "Agregar item al carrito", description = "Agrega un item por SKU. Si el SKU ya esta en el carrito suma la cantidad a la existente. 201 con Location; 400 si la entrada es invalida. Requiere JWT.")
    @PostMapping("/items")
    public ResponseEntity<CarritoItemResponse> agregarItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CarritoItemRequest request) {
        CarritoItem item = carritoService.agregarItem(jwt.getSubject(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sku}")
                .buildAndExpand(item.getSku())
                .toUri();
        return ResponseEntity.created(location).body(CarritoItemResponse.desde(item));
    }

    /**
     * PUT /api/v1/carrito/items/{sku}
     * Reemplaza la cantidad de un item existente. 200 en exito;
     * 404 si el SKU no esta en el carrito; 400 si la cantidad es menor que 1.
     * Requiere JWT.
     */
    @Operation(summary = "Actualizar cantidad de un item", description = "Reemplaza la cantidad del item identificado por SKU. 200 en exito; 404 si el SKU no esta en el carrito; 400 si la cantidad es menor que 1. Requiere JWT.")
    @PutMapping("/items/{sku}")
    public ResponseEntity<CarritoItemResponse> actualizarItem(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(name = "sku", description = "SKU del item en el carrito.", required = true)
            @PathVariable String sku,
            @Valid @RequestBody CarritoItemUpdateRequest request) {
        CarritoItem item = carritoService.actualizarItem(jwt.getSubject(), sku, request.cantidad());
        return ResponseEntity.ok(CarritoItemResponse.desde(item));
    }

    /**
     * DELETE /api/v1/carrito/items/{sku}
     * Elimina un item del carrito. 204 en exito; 404 si el SKU no existe.
     * Requiere JWT.
     */
    @Operation(summary = "Eliminar un item del carrito", description = "Elimina el item identificado por SKU. 204 en exito; 404 si el SKU no esta en el carrito. Requiere JWT.")
    @DeleteMapping("/items/{sku}")
    public ResponseEntity<Void> eliminarItem(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(name = "sku", description = "SKU del item a eliminar.", required = true)
            @PathVariable String sku) {
        carritoService.eliminarItem(jwt.getSubject(), sku);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/carrito
     * Vacia todos los items del carrito propio. 204 siempre (idempotente).
     * Requiere JWT.
     */
    @Operation(summary = "Vaciar carrito",
            description = "Elimina todos los items del carrito del usuario. 204 siempre (idempotente, aunque ya estuviera vacio). Requiere JWT; las llamadas MS->MS (rol SERVICIO) usan el query param usuarioSub.")
    @DeleteMapping
    public ResponseEntity<Void> vaciar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String usuarioSub) {
        carritoService.vaciar(subPropietario(jwt, usuarioSub));
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/carrito/validar
     * Valida el carrito y lo devuelve con items y advertencias simples
     * (vacio, cantidad total de unidades, precios sin verificar).
     * Sin integracion externa en esta fase. Requiere JWT.
     */
    @Operation(summary = "Validar carrito", description = "Devuelve el carrito con items y advertencias simples (vacio, cantidad total de unidades, precios sin verificar). Sin validacion de stock/precio externa en esta fase. Requiere JWT.")
    @PostMapping("/validar")
    public ResponseEntity<CarritoResponse> validar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(carritoService.validar(jwt.getSubject()));
    }
}
