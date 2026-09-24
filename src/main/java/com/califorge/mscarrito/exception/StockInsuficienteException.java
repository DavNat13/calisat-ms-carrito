package com.califorge.mscarrito.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String sku, int solicitado, int disponible) {
        super("Stock insuficiente para el SKU '" + sku + "': se solicitaron "
                + solicitado + " y solo hay " + disponible + " disponibles");
    }
}
