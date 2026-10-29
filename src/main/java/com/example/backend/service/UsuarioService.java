package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.UsuarioActualizarRolRequestDTO;
import com.example.backend.dto.UsuarioRegistroRequestDTO;
import com.example.backend.dto.UsuarioResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface UsuarioService {

    // HU-02: registrar un colaborador en la empresa del administrador
    UsuarioResponseDTO registrarUsuario(UsuarioSesion sesion, UsuarioRegistroRequestDTO request);

    List<UsuarioResponseDTO> listarUsuarios(UsuarioSesion sesion);

    UsuarioResponseDTO cambiarRol(UsuarioSesion sesion, Long usuarioId, UsuarioActualizarRolRequestDTO request);

    UsuarioResponseDTO desactivarUsuario(UsuarioSesion sesion, Long usuarioId);
}
