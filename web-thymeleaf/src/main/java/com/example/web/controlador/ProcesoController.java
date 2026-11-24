package com.example.web.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.web.cliente.ApiCliente;
import com.example.web.cliente.ApiException;
import com.example.web.modelo.ProcesoDetalle;
import com.example.web.modelo.ProcesoFormulario;
import com.example.web.modelo.ProcesoResumen;
import com.example.web.modelo.Usuario;
import com.example.web.sesion.SesionWeb;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** Vistas de procesos: listado con busqueda y paginacion, creacion y detalle con historial. */
@Controller
public class ProcesoController {

    private final ApiCliente api;

    public ProcesoController(ApiCliente api) {
        this.api = api;
    }

    @ModelAttribute("usuario")
    public Usuario usuario(HttpSession sesion) {
        return SesionWeb.usuario(sesion);
    }

    @GetMapping("/")
    public String inicio() {
        return "redirect:/procesos";
    }

    @GetMapping("/procesos")
    public String listar(@RequestParam(defaultValue = "") String nombre,
            @RequestParam(defaultValue = "0") int pagina, HttpSession sesion, Model model) {
        model.addAttribute("nombre", nombre);
        model.addAttribute("pagina", api.listarProcesos(SesionWeb.token(sesion), nombre, Math.max(pagina, 0)));
        return "procesos/lista";
    }

    @GetMapping("/procesos/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("formulario", new ProcesoFormulario());
        return "procesos/formulario";
    }

    @PostMapping("/procesos")
    public String crear(@Valid @ModelAttribute("formulario") ProcesoFormulario formulario, BindingResult errores,
            HttpSession sesion, Model model, RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return "procesos/formulario";
        }
        try {
            ProcesoResumen creado = api.crearProceso(SesionWeb.token(sesion), formulario);
            redirect.addFlashAttribute("mensaje", "Proceso \"" + creado.nombre() + "\" creado en estado borrador.");
            return "redirect:/procesos/" + creado.id();
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            return "procesos/formulario";
        }
    }

    @GetMapping("/procesos/{id}")
    public String detalle(@PathVariable Long id, HttpSession sesion, Model model) {
        String token = SesionWeb.token(sesion);
        ProcesoDetalle proceso = api.obtenerProceso(token, id);
        Usuario usuario = SesionWeb.usuario(sesion);
        boolean propio = usuario != null && proceso.empresaId() != null && proceso.empresaId().equals(usuario.empresaId());

        model.addAttribute("proceso", proceso);
        model.addAttribute("propio", propio);
        // El historial solo esta disponible para los procesos de la propia empresa
        model.addAttribute("historial", propio ? api.obtenerHistorial(token, id) : java.util.List.of());
        return "procesos/detalle";
    }
}
