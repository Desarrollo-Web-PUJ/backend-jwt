package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EliminacionFlujoMensajeResponseDTO;
import com.example.backend.dto.FlujoMensajeRequestDTO;
import com.example.backend.dto.FlujoMensajeResponseDTO;
import com.example.backend.entity.EventoMensaje;
import com.example.backend.entity.FlujoMensaje;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.TipoEventoMensaje;
import com.example.backend.entity.TipoPool;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.FlujoMensajeMapper;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.FlujoMensajeRepository;
import com.example.backend.repository.PoolRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class FlujoMensajeServiceImpl implements FlujoMensajeService {

    private final FlujoMensajeRepository flujoRepository;
    private final EventoMensajeRepository eventoRepository;
    private final PoolRepository poolRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final FlujoMensajeMapper flujoMapper;

    public FlujoMensajeServiceImpl(
            FlujoMensajeRepository flujoRepository,
            EventoMensajeRepository eventoRepository,
            PoolRepository poolRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            FlujoMensajeMapper flujoMapper) {
        this.flujoRepository = flujoRepository;
        this.eventoRepository = eventoRepository;
        this.poolRepository = poolRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.flujoMapper = flujoMapper;
    }

    @Override
    public List<FlujoMensajeResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return flujoRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(flujoMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public FlujoMensajeResponseDTO crearFlujo(Long procesoId, FlujoMensajeRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        FlujoMensaje flujo = new FlujoMensaje();
        flujo.setProceso(proceso);
        flujo.setActivo(true);
        List<String> advertencias = aplicarDatos(flujo, request, procesoId, null);
        flujo = flujoRepository.save(flujo);

        historialService.registrar(proceso, sesion,
                "Se creó un flujo de mensaje desde '" + flujo.getOrigen().getNombreMensaje() + "'");

        return conAdvertencias(flujo, advertencias);
    }

    @Override
    @Transactional
    public FlujoMensajeResponseDTO editarFlujo(Long procesoId, Long flujoId, FlujoMensajeRequestDTO request,
                                               UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        FlujoMensaje flujo = obtenerFlujo(flujoId, procesoId);
        List<String> advertencias = aplicarDatos(flujo, request, procesoId, flujoId);
        flujo = flujoRepository.save(flujo);

        historialService.registrar(proceso, sesion,
                "Se editó un flujo de mensaje desde '" + flujo.getOrigen().getNombreMensaje() + "'");

        return conAdvertencias(flujo, advertencias);
    }

    @Override
    @Transactional
    public EliminacionFlujoMensajeResponseDTO eliminarFlujo(Long procesoId, Long flujoId, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        FlujoMensaje flujo = obtenerFlujo(flujoId, procesoId);
        flujo.setActivo(false);
        flujoRepository.save(flujo);

        historialService.registrar(proceso, sesion,
                "Se eliminó un flujo de mensaje desde '" + flujo.getOrigen().getNombreMensaje() + "'");

        return new EliminacionFlujoMensajeResponseDTO("Flujo de mensaje eliminado correctamente", List.of());
    }

    /**
     * Valida y aplica origen y destino. Los mensajes sí cruzan pools (a diferencia
     * de los arcos): el destino es un Catch de otro pool o un pool externo (HU-25, HU-26).
     */
    private List<String> aplicarDatos(FlujoMensaje flujo, FlujoMensajeRequestDTO request, Long procesoId,
                                      Long flujoIdActual) {
        boolean tieneDestinoEvento = request.getDestinoId() != null;
        boolean tieneDestinoPool = request.getPoolDestinoId() != null;

        if (tieneDestinoEvento == tieneDestinoPool) {
            throw new OperacionInvalidaException(
                    "Debe indicar un único destino: un Message Catch o un pool externo");
        }
        if (request.getOrigenId() == null) {
            throw new OperacionInvalidaException("Debe indicar el Message Throw de origen");
        }

        EventoMensaje origen = obtenerEvento(request.getOrigenId(), procesoId, "Evento origen no encontrado");
        if (origen.getTipo() != TipoEventoMensaje.THROW) {
            throw new OperacionInvalidaException("El flujo de mensaje debe partir de un Message Throw");
        }

        List<String> advertencias = new ArrayList<>();
        EventoMensaje destino = null;
        Pool poolDestino = null;

        if (tieneDestinoEvento) {
            destino = obtenerEvento(request.getDestinoId(), procesoId, "Evento destino no encontrado");
            validarDestinoEvento(origen, destino);
            validarNoDuplicadoHaciaEvento(procesoId, origen.getId(), destino.getId(), flujoIdActual);
            advertencias.addAll(advertenciasDeCoincidencia(origen, destino));
        } else {
            poolDestino = poolRepository.findByIdAndProcesoIdAndActivoTrue(request.getPoolDestinoId(), procesoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Pool destino no encontrado"));
            validarDestinoExterno(origen, poolDestino);
            validarNoDuplicadoHaciaPool(procesoId, origen.getId(), poolDestino.getId(), flujoIdActual);
        }

        flujo.setOrigen(origen);
        flujo.setDestino(destino);
        flujo.setPoolDestino(poolDestino);
        flujo.setEtiqueta(request.getEtiqueta() == null || request.getEtiqueta().isBlank()
                ? null : request.getEtiqueta().trim());
        return advertencias;
    }

    private void validarDestinoEvento(EventoMensaje origen, EventoMensaje destino) {
        if (destino.getTipo() == TipoEventoMensaje.THROW) {
            throw new OperacionInvalidaException("El destino de un flujo de mensaje debe ser un Message Catch");
        }
        if (destino.getPool().getId().equals(origen.getPool().getId())) {
            throw new OperacionInvalidaException("Un flujo de mensaje solo es válido si cruza de un pool a otro");
        }
    }

    // HU-26: el envío a un sistema externo documenta el tipo de destino y qué pasa si falla
    private void validarDestinoExterno(EventoMensaje origen, Pool poolDestino) {
        if (poolDestino.getTipo() != TipoPool.EXTERNO) {
            throw new OperacionInvalidaException("El pool destino externo debe tener tipo EXTERNO");
        }
        if (poolDestino.getId().equals(origen.getPool().getId())) {
            throw new OperacionInvalidaException("El pool destino del mensaje debe ser distinto al pool origen");
        }
        if (origen.getTipoDestinoExterno() == null) {
            throw new OperacionInvalidaException("Debe indicar el tipo de destino externo en el Message Throw");
        }
        if (origen.getComportamientoFallo() == null) {
            throw new OperacionInvalidaException("Debe indicar el comportamiento ante fallo en el Message Throw");
        }
    }

    private List<String> advertenciasDeCoincidencia(EventoMensaje origen, EventoMensaje destino) {
        List<String> advertencias = new ArrayList<>();

        if (!origen.getNombreMensaje().equals(destino.getNombreMensaje())) {
            advertencias.add("El nombre del mensaje del origen ('" + origen.getNombreMensaje()
                    + "') no coincide con el del destino ('" + destino.getNombreMensaje() + "')");
        }

        // HU-28: si ambos definen correlación, debe ser la misma
        String claveOrigen = origen.getClaveCorrelacion();
        String claveDestino = destino.getClaveCorrelacion();
        if (claveOrigen != null && claveDestino != null && !claveOrigen.equals(claveDestino)) {
            advertencias.add("La clave de correlación del Message Throw no coincide con la del Message Catch destino");
        }
        return advertencias;
    }

    private void validarNoDuplicadoHaciaEvento(Long procesoId, Long origenId, Long destinoId, Long flujoIdActual) {
        boolean duplicado = flujoIdActual == null
                ? flujoRepository.existsByProcesoIdAndOrigenIdAndDestinoIdAndActivoTrue(procesoId, origenId, destinoId)
                : flujoRepository.existsByProcesoIdAndOrigenIdAndDestinoIdAndActivoTrueAndIdNot(
                        procesoId, origenId, destinoId, flujoIdActual);
        if (duplicado) {
            throw new OperacionInvalidaException("Ya existe un flujo de mensaje activo entre esos eventos");
        }
    }

    private void validarNoDuplicadoHaciaPool(Long procesoId, Long origenId, Long poolDestinoId, Long flujoIdActual) {
        boolean duplicado = flujoIdActual == null
                ? flujoRepository.existsByProcesoIdAndOrigenIdAndPoolDestinoIdAndActivoTrue(
                        procesoId, origenId, poolDestinoId)
                : flujoRepository.existsByProcesoIdAndOrigenIdAndPoolDestinoIdAndActivoTrueAndIdNot(
                        procesoId, origenId, poolDestinoId, flujoIdActual);
        if (duplicado) {
            throw new OperacionInvalidaException("Ya existe un flujo de mensaje activo hacia ese pool externo");
        }
    }

    private FlujoMensajeResponseDTO conAdvertencias(FlujoMensaje flujo, List<String> advertencias) {
        FlujoMensajeResponseDTO dto = flujoMapper.toDTO(flujo);
        dto.setAdvertencias(advertencias);
        return dto;
    }

    private FlujoMensaje obtenerFlujo(Long flujoId, Long procesoId) {
        return flujoRepository.findByIdAndProcesoIdAndActivoTrue(flujoId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Flujo de mensaje no encontrado"));
    }

    private EventoMensaje obtenerEvento(Long eventoId, Long procesoId, String mensajeNoEncontrado) {
        return eventoRepository.findByIdAndProcesoIdAndActivoTrue(eventoId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(mensajeNoEncontrado));
    }
}
