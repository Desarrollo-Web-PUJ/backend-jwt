package com.example.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.LoginRequestDTO;
import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.entity.Usuario;
import com.example.backend.exception.CredencialesInvalidasException;
import com.example.backend.exception.SesionNoAutenticadaException;
import com.example.backend.mapper.UsuarioMapper;
import com.example.backend.repository.UsuarioRepository;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    // Mismo mensaje para correo inexistente, usuario inactivo o contraseña
    // incorrecta: no se revela si el correo está registrado (HU-03).
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    public AuthServiceImpl(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
    }

    @Override
    public LoginResponseDTO iniciarSesion(LoginRequestDTO request) {
        String correo = request.getCorreo().trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new CredencialesInvalidasException(CREDENCIALES_INVALIDAS));

        if (!Boolean.TRUE.equals(usuario.getActivo())
                || !passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new CredencialesInvalidasException(CREDENCIALES_INVALIDAS);
        }

        return usuarioMapper.toLoginDTO(usuario);
    }

    @Override
    public LoginResponseDTO obtenerUsuarioActivo(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                .map(usuarioMapper::toLoginDTO)
                .orElseThrow(() -> new SesionNoAutenticadaException(
                        "La sesión ya no es válida. Inicie sesión nuevamente"));
    }
}
