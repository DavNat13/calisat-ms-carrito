package com.califorge.mscarrito.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CarritoItemUpdateRequest(
        @Schema(description = "Nueva cantidad absoluta del item (mayor o igual a 1).", example = "5")
        @NotNull(message = "cantidad es obligatoria")
        @Min(value = 1, message = "cantidad debe ser al menos 1")
        Integer cantidad) {
}
