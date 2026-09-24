package com.califorge.mscarrito.exception;

public class CantidadInvalidaException extends RuntimeException {

    public CantidadInvalidaException(int cantidad) {
        super("La cantidad debe ser un numero entero positivo; recibido: " + cantidad);
    }
}
