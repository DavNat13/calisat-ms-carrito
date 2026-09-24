package com.califorge.mscarrito.exception;

public class ItemCarritoNoEncontradoException extends RuntimeException {

    public ItemCarritoNoEncontradoException(String sku) {
        super("No existe ningun item con el SKU '" + sku + "' en el carrito del usuario");
    }
}
