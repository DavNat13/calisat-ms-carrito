package com.califorge.mscarrito.client;

/**
 * Vista minima del registro de stock en calisat-ms-inventario
 * (GET /api/v1/stock/sku/{sku}); solo los campos que consume el carrito.
 */
public record StockDto(
        String sku,
        Integer cantidadDisponible,
        Integer cantidadReservada) {
}
