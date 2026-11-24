package com.example.web.controlador;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.example.web.cliente.ApiException;
import com.example.web.cliente.SesionExpiradaException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Traduce los errores de la API a vistas. */
@ControllerAdvice
public class ManejadorErrores {

    /** Token vencido o invalido: se cierra la sesion y se vuelve al inicio de sesion. */
    @ExceptionHandler(SesionExpiradaException.class)
    public String sesionExpirada(HttpSession sesion) {
        sesion.invalidate();
        return "redirect:/login?expirada";
    }

    /** 403, 404 y otros errores de la API: pagina de error con el mensaje del backend. */
    @ExceptionHandler(ApiException.class)
    public String errorApi(ApiException e, Model model, HttpServletResponse response) {
        response.setStatus(e.getEstado());
        model.addAttribute("estado", e.getEstado());
        model.addAttribute("mensaje", e.getMessage());
        return "error-api";
    }
}
