package com.example.backend.mapper;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.backend.dto.CampoMensajeDTO;
import com.example.backend.dto.EventoMensajeResponseDTO;
import com.example.backend.entity.EventoMensaje;
import com.example.backend.exception.OperacionInvalidaException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conversión de {@link EventoMensaje} a su DTO y manejo de los campos que la
 * entidad guarda como texto JSON (campos del mensaje y actividades consumidoras).
 */
@Component
public class EventoMensajeMapper {

    private static final Logger log = LoggerFactory.getLogger(EventoMensajeMapper.class);

    private static final TypeReference<List<CampoMensajeDTO>> TIPO_CAMPOS = new TypeReference<>() {};
    private static final TypeReference<List<Long>> TIPO_IDS = new TypeReference<>() {};

    private final JsonMapper jsonMapper;

    public EventoMensajeMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public EventoMensajeResponseDTO toDTO(EventoMensaje evento) {
        EventoMensajeResponseDTO dto = new EventoMensajeResponseDTO();
        dto.setId(evento.getId());
        dto.setProcesoId(evento.getProceso().getId());
        dto.setPoolId(evento.getPool().getId());
        dto.setPoolNombre(evento.getPool().getNombre());
        if (evento.getLane() != null) {
            dto.setLaneId(evento.getLane().getId());
            dto.setLaneNombre(evento.getLane().getNombre());
        }
        dto.setTipo(evento.getTipo());
        dto.setNombreMensaje(evento.getNombreMensaje());
        dto.setClaveCorrelacion(evento.getClaveCorrelacion());
        dto.setCorrelacionDefinida(evento.getCorrelacionDefinida());
        dto.setComportamientoSinCorrelacion(evento.getComportamientoSinCorrelacion());
        dto.setOrigenExterno(evento.getOrigenExterno());
        dto.setTipoDestinoExterno(evento.getTipoDestinoExterno());
        dto.setComportamientoFallo(evento.getComportamientoFallo());
        dto.setPosicionX(evento.getPosicionX());
        dto.setPosicionY(evento.getPosicionY());
        dto.setCampos(leerJson(evento.getCamposMensaje(), TIPO_CAMPOS, evento.getId()));
        dto.setActividadesConsumidoras(leerJson(evento.getActividadesConsumidoras(), TIPO_IDS, evento.getId()));
        return dto;
    }

    public String camposAJson(List<CampoMensajeDTO> campos) {
        return escribirJson(campos == null ? List.of() : campos);
    }

    public String idsAJson(List<Long> ids) {
        return escribirJson(ids == null ? List.of() : ids);
    }

    private <T> List<T> leerJson(String json, TypeReference<List<T>> tipo, Long eventoId) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, tipo);
        } catch (JacksonException ex) {
            // Un dato corrupto no debe impedir mostrar el diagrama: se registra y se omite.
            log.warn("No se pudo leer el JSON del evento de mensaje {}: {}", eventoId, ex.getMessage());
            return List.of();
        }
    }

    private String escribirJson(Object valor) {
        try {
            return jsonMapper.writeValueAsString(valor);
        } catch (JacksonException ex) {
            throw new OperacionInvalidaException("Los campos del mensaje no tienen un formato válido");
        }
    }
}
