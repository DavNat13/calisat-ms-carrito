package com.califorge.mscarrito.exception;

public class SkuNoEncontradoException extends RuntimeException {

    public SkuNoEncontradoException(String sku) {
        super("El SKU '" + sku + "' no existe o esta inactivo en el catalogo");
    }
}
