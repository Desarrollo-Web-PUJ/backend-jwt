package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.backend.dto.LoginRequestDTO;
import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.entity.Usuario;
import com.example.backend.exception.CredencialesInvalidasException;
import com.example.backend.exception.SesionNoAutenticadaException;
import com.example.backend.mapper.UsuarioMapper;
import com.example.backend.repository.UsuarioRepository;

/** Pruebas unitarias del inicio de sesión (HU-03) con repositorio, encoder y mapper simulados. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioMapper usuarioMapper;

    @InjectMocks
    private AuthServiceImpl servicio;

    @Test
    @DisplayName("Credenciales correctas: normaliza el correo y devuelve el usuario")
    void loginCorrecto() {
        Usuario usuario = usuario(true);
        LoginResponseDTO esperado = org.mockito.Mockito.mock(LoginResponseDTO.class);
        when(usuarioRepository.findByCorreoIgnoreCase("ana@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Clave12345", "hash")).thenReturn(true);
        when(usuarioMapper.toLoginDTO(usuario)).thenReturn(esperado);

        assertSame(esperado, servicio.iniciarSesion(login("  ANA@Empresa.com ", "Clave12345")));
    }

    @Test
    @DisplayName("Contraseña incorrecta: mensaje genérico")
    void claveIncorrecta() {
        when(usuarioRepository.findByCorreoIgnoreCase("ana@empresa.com")).thenReturn(Optional.of(usuario(true)));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        CredencialesInvalidasException e = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.iniciarSesion(login("ana@empresa.com", "mala")));
        assertEquals("Correo o contraseña incorrectos", e.getMessage());
    }

    @Test
    @DisplayName("Correo inexistente: el mismo mensaje genérico")
    void correoInexistente() {
        when(usuarioRepository.findByCorreoIgnoreCase("nadie@empresa.com")).thenReturn(Optional.empty());

        CredencialesInvalidasException e = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.iniciarSesion(login("nadie@empresa.com", "x")));
        assertEquals("Correo o contraseña incorrectos", e.getMessage());
    }

    @Test
    @DisplayName("Usuario desactivado no inicia sesión, aunque la contraseña sea correcta")
    void usuarioInactivo() {
        when(usuarioRepository.findByCorreoIgnoreCase("ana@empresa.com")).thenReturn(Optional.of(usuario(false)));

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.iniciarSesion(login("ana@empresa.com", "Clave12345")));
        verify(usuarioMapper, never()).toLoginDTO(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Un token de un usuario desactivado deja de ser válido")
    void sesionDeUsuarioInactivo() {
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario(false)));
        assertThrows(SesionNoAutenticadaException.class, () -> servicio.obtenerUsuarioActivo(7L));
    }

    private static Usuario usuario(boolean activo) {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setCorreo("ana@empresa.com");
        u.setPassword("hash");
        u.setActivo(activo);
        return u;
    }

    private static LoginRequestDTO login(String correo, String clave) {
        LoginRequestDTO r = new LoginRequestDTO();
        r.setCorreo(correo);
        r.setPassword(clave);
        return r;
    }
}
