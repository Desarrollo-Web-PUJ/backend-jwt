package com.example.web.cliente;

/** La API respondio 401: el token no es valido o vencio. */
public class SesionExpiradaException extends RuntimeException {

    public SesionExpiradaException() {
        super("La sesion vencio, inicie sesion de nuevo");
    }
}
