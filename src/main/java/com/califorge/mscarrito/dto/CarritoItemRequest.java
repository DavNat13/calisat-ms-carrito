package com.califorge.mscarrito.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CarritoItemRequest(
        @Schema(description = "SKU del producto a agregar al carrito.", example = "ANILLAS-001", maxLength = 64)
        @NotBlank(message = "sku es obligatorio")
        @Size(max = 64, message = "sku no puede superar 64 caracteres")
        String sku,

        @Schema(description = "Cantidad a agregar (mayor o igual a 1). Si el SKU ya existe se suma a la actual.", example = "2")
        @NotNull(message = "cantidad es obligatoria")
        @Min(value = 1, message = "cantidad debe ser al menos 1")
        Integer cantidad) {
}
