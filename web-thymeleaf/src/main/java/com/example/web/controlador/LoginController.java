package com.example.web.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.web.cliente.ApiCliente;
import com.example.web.cliente.ApiException;
import com.example.web.modelo.TokenRespuesta;
import com.example.web.sesion.SesionWeb;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Inicio y cierre de sesion contra la API (POST /api/auth/login). */
@Controller
public class LoginController {

    private final ApiCliente api;

    public LoginController(ApiCliente api) {
        this.api = api;
    }

    @GetMapping("/login")
    public String formulario() {
        return "login";
    }

    @PostMapping("/login")
    public String iniciarSesion(@RequestParam String correo, @RequestParam String password,
            HttpServletRequest request, Model model) {
        try {
            TokenRespuesta respuesta = api.iniciarSesion(correo.trim(), password);
            // Sesion nueva en cada inicio de sesion (evita la fijacion de sesion)
            HttpSession anterior = request.getSession(false);
            if (anterior != null) {
                anterior.invalidate();
            }
            SesionWeb.iniciar(request.getSession(true), respuesta.token(), respuesta.usuario());
            return "redirect:/procesos";
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("correo", correo);
            return "login";
        }
    }

    @PostMapping("/logout")
    public String cerrarSesion(HttpSession sesion) {
        sesion.invalidate();
        return "redirect:/login?salida";
    }
}
