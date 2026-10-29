package com.example.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EmpresaRegistroRequestDTO;
import com.example.backend.dto.EmpresaResponseDTO;
import com.example.backend.entity.Empresa;
import com.example.backend.entity.RolUsuario;
import com.example.backend.entity.Usuario;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.mapper.EmpresaMapper;
import com.example.backend.repository.EmpresaRepository;
import com.example.backend.repository.UsuarioRepository;

@Service
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmpresaMapper empresaMapper;

    public EmpresaServiceImpl(
            EmpresaRepository empresaRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EmpresaMapper empresaMapper) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.empresaMapper = empresaMapper;
    }

    // HU-01: la empresa y su administrador inicial se crean en una sola transacción
    @Override
    @Transactional
    public EmpresaResponseDTO registrarEmpresa(EmpresaRegistroRequestDTO request) {
        String nit = request.getNit().trim();
        String adminCorreo = request.getAdminCorreo().trim().toLowerCase();

        if (empresaRepository.existsByNitIgnoreCase(nit)) {
            throw new RecursoDuplicadoException("Ya existe una empresa registrada con ese NIT");
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(adminCorreo)) {
            throw new RecursoDuplicadoException("Ya existe un usuario registrado con ese correo");
        }

        Empresa empresa = new Empresa();
        empresa.setNombre(request.getNombre().trim());
        empresa.setNit(nit);
        empresa.setCorreoContacto(request.getCorreoContacto().trim());
        empresa = empresaRepository.save(empresa);

        Usuario administrador = new Usuario();
        administrador.setNombre(request.getAdminNombre().trim());
        administrador.setCorreo(adminCorreo);
        administrador.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        administrador.setRol(RolUsuario.ADMINISTRADOR);
        administrador.setActivo(true);
        administrador.setEmpresa(empresa);
        administrador = usuarioRepository.save(administrador);

        return empresaMapper.toDTO(empresa, administrador);
    }
}
