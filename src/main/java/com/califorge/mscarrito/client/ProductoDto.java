package com.califorge.mscarrito.client;

import java.math.BigDecimal;

/**
 * Vista minima del producto en calisat-ms-catalogo
 * (GET /api/v1/catalogo/{sku}); solo los campos que consume el carrito.
 */
public record ProductoDto(
        String sku,
        String nombre,
        BigDecimal precio,
        boolean activo) {
}
