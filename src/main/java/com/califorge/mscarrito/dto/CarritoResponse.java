package com.califorge.mscarrito.dto;

import com.califorge.mscarrito.model.Carrito;
import com.califorge.mscarrito.model.CarritoItem;
import com.califorge.mscarrito.model.EstadoCarrito;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CarritoResponse(
        @Schema(description = "Identificador del carrito.", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID id,

        @Schema(description = "Sub (oid) de Azure AD del propietario del carrito.", example = "e5372bf0-c5e3-4286-887c-79069f209c1f")
        String usuarioSub,

        @Schema(description = "Estado del carrito.", example = "ABIERTO")
        EstadoCarrito estado,

        @Schema(description = "Items del carrito.")
        List<CarritoItemResponse> items,

        @Schema(description = "Fecha de ultima actualizacion del carrito.")
        LocalDateTime fechaActualizacion,

        @Schema(description = "Advertencias simples detectadas en la validacion (vacio, total de unidades, precios sin verificar). Vacio en lecturas normales.")
        List<String> advertencias) {

    public static CarritoResponse desde(Carrito carrito, List<CarritoItem> items, List<String> advertencias) {
        List<CarritoItemResponse> itemsResponse = items == null
                ? List.of()
                : items.stream().map(CarritoItemResponse::desde).toList();
        return new CarritoResponse(
                carrito.getId(),
                carrito.getUsuarioSub(),
                carrito.getEstado(),
                itemsResponse,
                carrito.getFechaActualizacion(),
                advertencias == null ? List.of() : List.copyOf(advertencias));
    }

    public static CarritoResponse desde(Carrito carrito, List<CarritoItem> items) {
        return desde(carrito, items, List.of());
    }
}
