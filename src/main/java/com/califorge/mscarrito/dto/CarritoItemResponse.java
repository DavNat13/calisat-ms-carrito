package com.califorge.mscarrito.dto;

import com.califorge.mscarrito.model.CarritoItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record CarritoItemResponse(
        @Schema(description = "Identificador interno del item.", example = "3f1d2f6e-8a4b-4c9d-9e2f-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "SKU del producto en el carrito.", example = "ANILLAS-001")
        String sku,

        @Schema(description = "Cantidad en el carrito.", example = "2")
        Integer cantidad,

        @Schema(description = "Snapshot de precio visto al agregar el item (null hasta la fase de integracion con catalogo).", example = "19.99")
        BigDecimal precioUnitarioVisto) {

    public static CarritoItemResponse desde(CarritoItem item) {
        return new CarritoItemResponse(
                item.getId(),
                item.getSku(),
                item.getCantidad(),
                item.getPrecioUnitarioVisto());
    }
}
