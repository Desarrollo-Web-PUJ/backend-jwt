package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.dto.UsuarioResponseDTO;
import com.example.backend.entity.Usuario;

/**
 * Conversión de la entidad {@link Usuario} a sus DTO. Nunca expone la contraseña.
 */
@Component
public class UsuarioMapper {

    public UsuarioResponseDTO toDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getActivo(),
                usuario.getEmpresa().getId()
        );
    }

    public LoginResponseDTO toLoginDTO(Usuario usuario) {
        return new LoginResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getEmpresa().getId()
        );
    }
}
