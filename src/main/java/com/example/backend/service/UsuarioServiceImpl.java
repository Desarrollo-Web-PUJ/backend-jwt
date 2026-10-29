package com.example.backend.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.UsuarioActualizarRolRequestDTO;
import com.example.backend.dto.UsuarioRegistroRequestDTO;
import com.example.backend.dto.UsuarioResponseDTO;
import com.example.backend.entity.Empresa;
import com.example.backend.entity.Usuario;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.UsuarioMapper;
import com.example.backend.repository.EmpresaRepository;
import com.example.backend.repository.UsuarioRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private static final String ACCION = "gestionar usuarios";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidadorAcceso validadorAcceso;
    private final UsuarioMapper usuarioMapper;

    public UsuarioServiceImpl(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            PasswordEncoder passwordEncoder,
            ValidadorAcceso validadorAcceso,
            UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
        this.validadorAcceso = validadorAcceso;
        this.usuarioMapper = usuarioMapper;
    }

    @Override
    @Transactional
    public UsuarioResponseDTO registrarUsuario(UsuarioSesion sesion, UsuarioRegistroRequestDTO request) {
        validadorAcceso.exigirAdministrador(sesion.rol(), ACCION);

        Empresa empresa = empresaRepository.findById(sesion.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa de la sesión no existe"));

        String correo = request.getCorreo().trim().toLowerCase();
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new RecursoDuplicadoException("Ya existe un usuario registrado con ese correo");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre().trim());
        usuario.setCorreo(correo);
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(request.getRol());
        usuario.setActivo(true);
        usuario.setEmpresa(empresa);

        return usuarioMapper.toDTO(usuarioRepository.save(usuario));
    }

    @Override
    public List<UsuarioResponseDTO> listarUsuarios(UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), ACCION);

        return usuarioRepository.findByEmpresaIdOrderByNombreAsc(sesion.empresaId())
                .stream()
                .map(usuarioMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioResponseDTO cambiarRol(UsuarioSesion sesion, Long usuarioId,
                                         UsuarioActualizarRolRequestDTO request) {
        Usuario usuario = obtenerOtroUsuarioDeLaEmpresa(sesion, usuarioId);
        usuario.setRol(request.getRol());
        return usuarioMapper.toDTO(usuarioRepository.save(usuario));
    }

    // Desactivar conserva el usuario y su historial (HU-02): no se borra.
    @Override
    @Transactional
    public UsuarioResponseDTO desactivarUsuario(UsuarioSesion sesion, Long usuarioId) {
        Usuario usuario = obtenerOtroUsuarioDeLaEmpresa(sesion, usuarioId);
        usuario.setActivo(false);
        return usuarioMapper.toDTO(usuarioRepository.save(usuario));
    }

    // Un administrador no puede cambiar su propio rol ni desactivarse: así la
    // empresa nunca se queda sin quien la administre.
    private Usuario obtenerOtroUsuarioDeLaEmpresa(UsuarioSesion sesion, Long usuarioId) {
        validadorAcceso.exigirAdministrador(sesion.rol(), ACCION);

        if (usuarioId.equals(sesion.usuarioId())) {
            throw new OperacionInvalidaException(
                    "No puede cambiar el rol ni desactivar su propia cuenta");
        }

        return usuarioRepository.findByIdAndEmpresaId(usuarioId, sesion.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe en la empresa"));
    }
}
