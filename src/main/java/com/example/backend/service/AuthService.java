package com.example.backend.service;

import com.example.backend.dto.LoginRequestDTO;
import com.example.backend.dto.LoginResponseDTO;

public interface AuthService {

    // HU-03: Inicio de sesión
    LoginResponseDTO iniciarSesion(LoginRequestDTO request);

    // Datos vigentes del usuario de la sesión; falla si ya no existe o está inactivo.
    LoginResponseDTO obtenerUsuarioActivo(Long usuarioId);
}
