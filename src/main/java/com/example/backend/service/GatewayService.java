package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.EdicionGatewayResponseDTO;
import com.example.backend.dto.EliminacionGatewayResponseDTO;
import com.example.backend.dto.GatewayRequestDTO;
import com.example.backend.dto.GatewayResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface GatewayService {

    List<GatewayResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion);

    // HU-14: Crear gateway
    GatewayResponseDTO crearGateway(Long procesoId, GatewayRequestDTO request, UsuarioSesion sesion);

    // HU-15: Editar gateway
    EdicionGatewayResponseDTO editarGateway(Long procesoId, Long gatewayId, GatewayRequestDTO request,
                                            UsuarioSesion sesion);

    // HU-16: Eliminar gateway (requiere confirmación explícita)
    EliminacionGatewayResponseDTO eliminarGateway(Long procesoId, Long gatewayId, boolean confirmar,
                                                  UsuarioSesion sesion);
}
