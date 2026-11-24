package com.example.web.sesion;

import com.example.web.modelo.Usuario;

import jakarta.servlet.http.HttpSession;

/**
 * Datos guardados en la sesion de la aplicacion web (del lado del servidor).
 *
 * El JWT nunca llega al navegador: solo viaja entre esta aplicacion y la API.
 * El navegador recibe una cookie de sesion HttpOnly.
 */
public final class SesionWeb {

    static final String TOKEN = "jwt";
    static final String USUARIO = "usuario";

    private SesionWeb() {
    }

    public static void iniciar(HttpSession sesion, String token, Usuario usuario) {
        sesion.setAttribute(TOKEN, token);
        sesion.setAttribute(USUARIO, usuario);
    }

    public static String token(HttpSession sesion) {
        return (String) sesion.getAttribute(TOKEN);
    }

    public static Usuario usuario(HttpSession sesion) {
        return (Usuario) sesion.getAttribute(USUARIO);
    }
}
