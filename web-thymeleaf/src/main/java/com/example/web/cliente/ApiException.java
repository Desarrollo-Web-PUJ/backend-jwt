package com.example.web.cliente;

/** Error devuelto por la API, con su codigo HTTP y el mensaje del campo "mensaje". */
public class ApiException extends RuntimeException {

    private final int estado;

    public ApiException(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public int getEstado() {
        return estado;
    }
}
