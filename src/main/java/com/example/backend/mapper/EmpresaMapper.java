package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.EmpresaResponseDTO;
import com.example.backend.entity.Empresa;
import com.example.backend.entity.Usuario;

/**
 * Conversión de la entidad {@link Empresa} a {@link EmpresaResponseDTO}.
 */
@Component
public class EmpresaMapper {

    public EmpresaResponseDTO toDTO(Empresa empresa, Usuario administrador) {
        return new EmpresaResponseDTO(
                empresa.getId(),
                empresa.getNombre(),
                empresa.getNit(),
                empresa.getCorreoContacto(),
                administrador.getId()
        );
    }
}
