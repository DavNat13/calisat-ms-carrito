package com.califorge.mscarrito.model;

/**
 * Estado del carrito. ABIERTO es el estado por defecto al crearse;
 * CERRADO queda reservado para flujos posteriores de cierre/checkout.
 */
public enum EstadoCarrito {
    ABIERTO,
    CERRADO
}
