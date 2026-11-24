package com.example.web.modelo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos del formulario de creacion de procesos. */
public class ProcesoFormulario {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre admite maximo 150 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripcion admite maximo 500 caracteres")
    private String descripcion;

    @NotBlank(message = "La categoria es obligatoria")
    @Size(max = 100, message = "La categoria admite maximo 100 caracteres")
    private String categoria;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }
}
