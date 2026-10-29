package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EliminacionEventoMensajeResponseDTO;
import com.example.backend.dto.EventoMensajeRequestDTO;
import com.example.backend.dto.EventoMensajeResponseDTO;
import com.example.backend.entity.Arco;
import com.example.backend.entity.EventoMensaje;
import com.example.backend.entity.FlujoMensaje;
import com.example.backend.entity.Lane;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.TipoEventoMensaje;
import com.example.backend.entity.TipoNodo;
import com.example.backend.entity.TipoPool;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.EventoMensajeMapper;
import com.example.backend.repository.ActividadRepository;
import com.example.backend.repository.ArcoRepository;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.FlujoMensajeRepository;
import com.example.backend.repository.LaneRepository;
import com.example.backend.repository.PoolRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class EventoMensajeServiceImpl implements EventoMensajeService {

    private static final List<TipoEventoMensaje> TIPOS_CATCH =
            List.of(TipoEventoMensaje.CATCH_INICIO, TipoEventoMensaje.CATCH_INTERMEDIO);

    private final EventoMensajeRepository eventoRepository;
    private final FlujoMensajeRepository flujoRepository;
    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ActividadRepository actividadRepository;
    private final ArcoRepository arcoRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final EventoMensajeMapper eventoMapper;

    public EventoMensajeServiceImpl(
            EventoMensajeRepository eventoRepository,
            FlujoMensajeRepository flujoRepository,
            PoolRepository poolRepository,
            LaneRepository laneRepository,
            ActividadRepository actividadRepository,
            ArcoRepository arcoRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            EventoMensajeMapper eventoMapper) {
        this.eventoRepository = eventoRepository;
        this.flujoRepository = flujoRepository;
        this.poolRepository = poolRepository;
        this.laneRepository = laneRepository;
        this.actividadRepository = actividadRepository;
        this.arcoRepository = arcoRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.eventoMapper = eventoMapper;
    }

    @Override
    public List<EventoMensajeResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return eventoRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(eventoMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public EventoMensajeResponseDTO crearEvento(Long procesoId, EventoMensajeRequestDTO request,
                                                UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Pool pool = obtenerPoolInterno(procesoId, request.getPoolId());
        Lane lane = request.getLaneId() != null ? obtenerLane(pool.getId(), request.getLaneId()) : null;

        EventoMensaje evento = new EventoMensaje();
        evento.setProceso(proceso);
        evento.setPool(pool);
        evento.setLane(lane);
        aplicarDatos(evento, request, procesoId, null);
        evento = eventoRepository.save(evento);

        historialService.registrar(proceso, sesion,
                "Se creó el evento de mensaje '" + evento.getNombreMensaje()
                        + "' (" + evento.getTipo() + ") en el pool '" + pool.getNombre() + "'");

        return conAdvertencias(evento, procesoId);
    }

    @Override
    @Transactional
    public EventoMensajeResponseDTO editarEvento(Long procesoId, Long eventoId, EventoMensajeRequestDTO request,
                                                 UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        EventoMensaje evento = obtenerEvento(eventoId, procesoId);
        Pool pool = obtenerPoolInterno(procesoId, request.getPoolId());
        Lane lane = request.getLaneId() != null ? obtenerLane(pool.getId(), request.getLaneId()) : null;

        validarEdicionConConexionesActivas(evento, request, pool, procesoId);

        evento.setPool(pool);
        evento.setLane(lane);
        aplicarDatos(evento, request, procesoId, eventoId);
        evento = eventoRepository.save(evento);

        historialService.registrar(proceso, sesion,
                "Se editó el evento de mensaje '" + evento.getNombreMensaje() + "'");

        return conAdvertencias(evento, procesoId);
    }

    @Override
    @Transactional
    public EliminacionEventoMensajeResponseDTO eliminarEvento(Long procesoId, Long eventoId, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        EventoMensaje evento = obtenerEvento(eventoId, procesoId);
        List<String> advertencias = new ArrayList<>();

        // Un mensaje sin su evento no tiene sentido: se eliminan sus flujos
        List<FlujoMensaje> flujos = flujoRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .filter(flujo -> conectaConEvento(flujo, eventoId))
                .toList();
        if (!flujos.isEmpty()) {
            flujos.forEach(flujo -> flujo.setActivo(false));
            flujoRepository.saveAll(flujos);
            advertencias.add("Se eliminaron también los flujos de mensaje asociados (" + flujos.size() + ")");
        }

        // Ni arcos sueltos: todo flujo de secuencia debe unir dos elementos
        List<Arco> arcos = new ArrayList<>();
        arcos.addAll(arcoRepository.findByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                procesoId, TipoNodo.EVENTO, eventoId));
        arcos.addAll(arcoRepository.findByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                procesoId, TipoNodo.EVENTO, eventoId));
        if (!arcos.isEmpty()) {
            arcos.forEach(arco -> arco.setActivo(false));
            arcoRepository.saveAll(arcos);
            advertencias.add("Se eliminaron también los arcos de secuencia asociados (" + arcos.size() + ")");
        }

        evento.setActivo(false);
        eventoRepository.save(evento);

        historialService.registrar(proceso, sesion,
                "Se eliminó el evento de mensaje '" + evento.getNombreMensaje() + "'");

        return new EliminacionEventoMensajeResponseDTO("Evento de mensaje eliminado correctamente", advertencias);
    }

    // ---------- Reglas del modelo ----------

    private void aplicarDatos(EventoMensaje evento, EventoMensajeRequestDTO request, Long procesoId,
                              Long eventoIdActual) {
        validarRequest(request);
        validarActividadesConsumidoras(request, procesoId);

        boolean esThrow = request.getTipo() == TipoEventoMensaje.THROW;

        evento.setTipo(request.getTipo());
        evento.setNombreMensaje(request.getNombreMensaje().trim());
        evento.setClaveCorrelacion(textoOpcional(request.getClaveCorrelacion()));
        evento.setCorrelacionDefinida(evento.getClaveCorrelacion() != null);
        evento.setOrigenExterno(!esThrow && Boolean.TRUE.equals(request.getOrigenExterno()));

        // Cada tipo de evento solo conserva los atributos que le aplican
        evento.setTipoDestinoExterno(esThrow ? request.getTipoDestinoExterno() : null);
        evento.setComportamientoFallo(esThrow ? request.getComportamientoFallo() : null);
        evento.setComportamientoSinCorrelacion(esThrow ? null : request.getComportamientoSinCorrelacion());

        evento.setPosicionX(request.getPosicionX());
        evento.setPosicionY(request.getPosicionY());

        validarDuplicado(evento, procesoId, eventoIdActual);

        evento.setCamposMensaje(eventoMapper.camposAJson(request.getCampos()));
        evento.setActividadesConsumidoras(eventoMapper.idsAJson(request.getActividadesConsumidoras()));
    }

    private EventoMensajeResponseDTO conAdvertencias(EventoMensaje evento, Long procesoId) {
        EventoMensajeResponseDTO dto = eventoMapper.toDTO(evento);
        dto.setAdvertencias(advertenciasDeModelado(evento, procesoId));
        return dto;
    }

    /** Problemas de modelado que no impiden guardar, pero que el editor debe advertir. */
    private List<String> advertenciasDeModelado(EventoMensaje evento, Long procesoId) {
        List<String> advertencias = new ArrayList<>();

        // HU-25: un Throw necesita un Catch con el mismo nombre en otro pool
        if (evento.getTipo() == TipoEventoMensaje.THROW) {
            boolean hayReceptorEnOtroPool = eventoRepository
                    .findByProcesoIdAndNombreMensajeAndTipoInAndActivoTrue(
                            procesoId, evento.getNombreMensaje(), TIPOS_CATCH)
                    .stream()
                    .anyMatch(receptor -> !receptor.getPool().getId().equals(evento.getPool().getId()));
            if (!hayReceptorEnOtroPool) {
                advertencias.add("El mensaje '" + evento.getNombreMensaje()
                        + "' queda sin receptor: no existe un Message Catch con ese nombre en otro pool.");
            }
        }

        // HU-27: un Catch intermedio debe saber a qué caso corresponde
        if (evento.getTipo() == TipoEventoMensaje.CATCH_INTERMEDIO && evento.getClaveCorrelacion() == null) {
            advertencias.add("El Message Catch intermedio '" + evento.getNombreMensaje()
                    + "' no tiene clave de correlación definida.");
        }

        // HU-27: un Catch de inicio necesita un emisor (Throw) o venir de un sistema externo
        if (evento.getTipo() == TipoEventoMensaje.CATCH_INICIO
                && !Boolean.TRUE.equals(evento.getOrigenExterno())
                && eventoRepository.findByProcesoIdAndNombreMensajeAndTipoInAndActivoTrue(
                        procesoId, evento.getNombreMensaje(), List.of(TipoEventoMensaje.THROW)).isEmpty()) {
            advertencias.add("El Message Catch de inicio '" + evento.getNombreMensaje()
                    + "' no tiene un Message Throw correspondiente ni está marcado como origen externo.");
        }

        // HU-28: mismo nombre y misma clave de correlación hacen el modelo ambiguo
        if (evento.getClaveCorrelacion() != null) {
            boolean ambiguo = eventoRepository
                    .findByProcesoIdAndNombreMensajeAndClaveCorrelacionAndActivoTrue(
                            procesoId, evento.getNombreMensaje(), evento.getClaveCorrelacion())
                    .stream()
                    .anyMatch(otro -> !otro.getId().equals(evento.getId()));
            if (ambiguo) {
                advertencias.add("Existen otros mensajes con el mismo nombre '" + evento.getNombreMensaje()
                        + "' y la misma clave de correlación '" + evento.getClaveCorrelacion()
                        + "': el modelo es ambiguo.");
            }
        }

        return advertencias;
    }

    private void validarRequest(EventoMensajeRequestDTO request) {
        TipoEventoMensaje tipo = request.getTipo();

        if (tipo == null) {
            throw new OperacionInvalidaException("El tipo de evento es obligatorio");
        }
        if (request.getNombreMensaje() == null || request.getNombreMensaje().isBlank()) {
            throw new OperacionInvalidaException("El nombre del mensaje es obligatorio");
        }
        if (tipo == TipoEventoMensaje.CATCH_INTERMEDIO && Boolean.TRUE.equals(request.getOrigenExterno())) {
            throw new OperacionInvalidaException("Solo un Message Catch de inicio puede marcarse como origen externo");
        }
        if (tipo != TipoEventoMensaje.THROW && request.getComportamientoFallo() != null) {
            throw new OperacionInvalidaException("El comportamiento ante fallo solo aplica a Message Throw");
        }
        if (tipo != TipoEventoMensaje.THROW && request.getTipoDestinoExterno() != null) {
            throw new OperacionInvalidaException("El tipo de destino externo solo aplica a Message Throw");
        }
        if (tipo == TipoEventoMensaje.THROW && request.getComportamientoSinCorrelacion() != null) {
            throw new OperacionInvalidaException("El comportamiento sin correlación solo aplica a Message Catch");
        }
        if (Boolean.TRUE.equals(request.getOrigenExterno()) && request.getComportamientoSinCorrelacion() == null) {
            throw new OperacionInvalidaException(
                    "Debe indicar el comportamiento para mensajes externos sin correlación");
        }
    }

    private void validarActividadesConsumidoras(EventoMensajeRequestDTO request, Long procesoId) {
        if (request.getActividadesConsumidoras() == null) {
            return;
        }
        for (Long actividadId : request.getActividadesConsumidoras()) {
            if (actividadId == null) {
                throw new OperacionInvalidaException("Las actividades consumidoras no pueden contener valores nulos");
            }
            if (actividadRepository.findByIdAndProcesoIdAndActivoTrue(actividadId, procesoId).isEmpty()) {
                throw new RecursoNoEncontradoException("La actividad consumidora " + actividadId
                        + " no existe, está eliminada o no pertenece al proceso");
            }
        }
    }

    private void validarEdicionConConexionesActivas(EventoMensaje evento, EventoMensajeRequestDTO request,
                                                    Pool poolNuevo, Long procesoId) {
        long flujosSalientes = flujoRepository.countByOrigenIdAndActivoTrue(evento.getId());
        long flujosEntrantes = flujoRepository.countByDestinoIdAndActivoTrue(evento.getId());
        boolean cambiaDePool = !evento.getPool().getId().equals(poolNuevo.getId());

        if (flujosSalientes > 0 && request.getTipo() != TipoEventoMensaje.THROW) {
            throw new OperacionInvalidaException(
                    "No se puede cambiar el tipo: el evento tiene flujos de mensaje salientes activos");
        }
        if (flujosEntrantes > 0 && request.getTipo() == TipoEventoMensaje.THROW) {
            throw new OperacionInvalidaException(
                    "No se puede cambiar el tipo: el evento tiene flujos de mensaje entrantes activos");
        }
        if (cambiaDePool && (flujosSalientes > 0 || flujosEntrantes > 0)) {
            throw new OperacionInvalidaException("No se puede cambiar el pool de un evento con flujos de mensaje activos");
        }

        boolean tieneArcos =
                !arcoRepository.findByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                        procesoId, TipoNodo.EVENTO, evento.getId()).isEmpty()
                || !arcoRepository.findByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                        procesoId, TipoNodo.EVENTO, evento.getId()).isEmpty();

        if (cambiaDePool && tieneArcos) {
            throw new OperacionInvalidaException(
                    "No se puede cambiar el pool de un evento conectado por arcos de secuencia activos");
        }
    }

    private void validarDuplicado(EventoMensaje evento, Long procesoId, Long eventoIdActual) {
        boolean duplicado = eventoRepository.existsDuplicadoActivo(
                procesoId,
                evento.getPool().getId(),
                evento.getTipo(),
                evento.getNombreMensaje(),
                evento.getClaveCorrelacion(),
                eventoIdActual);

        if (duplicado) {
            throw new OperacionInvalidaException(
                    "Ya existe un evento de mensaje activo con el mismo tipo, nombre, pool y correlación");
        }
    }

    private boolean conectaConEvento(FlujoMensaje flujo, Long eventoId) {
        return (flujo.getOrigen() != null && flujo.getOrigen().getId().equals(eventoId))
                || (flujo.getDestino() != null && flujo.getDestino().getId().equals(eventoId));
    }

    private EventoMensaje obtenerEvento(Long eventoId, Long procesoId) {
        return eventoRepository.findByIdAndProcesoIdAndActivoTrue(eventoId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento de mensaje no encontrado"));
    }

    // Un pool externo es caja negra: no contiene eventos internos (HU-21)
    private Pool obtenerPoolInterno(Long procesoId, Long poolId) {
        if (poolId == null) {
            throw new OperacionInvalidaException("Debe indicar el pool del evento");
        }
        Pool pool = poolRepository.findByIdAndProcesoIdAndActivoTrue(poolId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado"));
        if (pool.getTipo() == TipoPool.EXTERNO) {
            throw new OperacionInvalidaException(
                    "Un pool externo es caja negra: no puede contener eventos de mensaje internos");
        }
        return pool;
    }

    private Lane obtenerLane(Long poolId, Long laneId) {
        return laneRepository.findByIdAndPoolIdAndActivoTrue(laneId, poolId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada en el pool"));
    }

    private String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
