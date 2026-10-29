package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.ArcoRequestDTO;
import com.example.backend.dto.ArcoResponseDTO;
import com.example.backend.dto.EliminacionArcoResponseDTO;
import com.example.backend.entity.Actividad;
import com.example.backend.entity.Arco;
import com.example.backend.entity.EventoMensaje;
import com.example.backend.entity.Gateway;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.TipoEventoMensaje;
import com.example.backend.entity.TipoGateway;
import com.example.backend.entity.TipoNodo;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.ArcoMapper;
import com.example.backend.repository.ActividadRepository;
import com.example.backend.repository.ArcoRepository;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.GatewayRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class ArcoServiceImpl implements ArcoService {

    private final ArcoRepository arcoRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final EventoMensajeRepository eventoMensajeRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final ArcoMapper arcoMapper;

    public ArcoServiceImpl(
            ArcoRepository arcoRepository,
            ActividadRepository actividadRepository,
            GatewayRepository gatewayRepository,
            EventoMensajeRepository eventoMensajeRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            ArcoMapper arcoMapper) {

        this.arcoRepository = arcoRepository;
        this.actividadRepository = actividadRepository;
        this.gatewayRepository = gatewayRepository;
        this.eventoMensajeRepository = eventoMensajeRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.arcoMapper = arcoMapper;
    }

    @Override
    public List<ArcoResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return arcoRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(arcoMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ArcoResponseDTO crearArco(Long procesoId, ArcoRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        validarArco(procesoId, request, null);
        String condicion = resolverCondicionGateway(procesoId, request);

        // Si existió un arco idéntico eliminado lógicamente, se reactiva en lugar de duplicarlo
        Arco arco = arcoRepository
                .findFirstByProcesoIdAndTipoOrigenAndOrigenIdAndTipoDestinoAndDestinoIdAndActivoFalseOrderByIdDesc(
                        procesoId,
                        request.getTipoOrigen(), request.getOrigenId(),
                        request.getTipoDestino(), request.getDestinoId())
                .orElseGet(Arco::new);

        arco.setProceso(proceso);
        aplicarDatos(arco, request, condicion);
        arco.setActivo(true);
        arco = arcoRepository.save(arco);

        historialService.registrar(proceso, sesion, "Se creó el arco #" + arco.getId() + " " + describir(arco));

        return arcoMapper.toDTO(arco);
    }

    @Override
    @Transactional
    public ArcoResponseDTO editarArco(Long procesoId, Long arcoId, ArcoRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Arco arco = obtenerArco(arcoId, procesoId);

        validarArco(procesoId, request, arcoId);
        String condicion = resolverCondicionGateway(procesoId, request);

        aplicarDatos(arco, request, condicion);
        arco = arcoRepository.save(arco);

        historialService.registrar(proceso, sesion, "Se editó el arco #" + arco.getId() + " " + describir(arco));

        return arcoMapper.toDTO(arco);
    }

    // HU-13: advierte si algún nodo queda sin camino de entrada o de salida
    @Override
    @Transactional
    public EliminacionArcoResponseDTO eliminarArco(Long procesoId, Long arcoId, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "eliminar arcos");
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Arco arco = obtenerArco(arcoId, procesoId);

        List<String> advertencias = new ArrayList<>();

        long salidasOrigen = arcoRepository.countByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                procesoId, arco.getTipoOrigen(), arco.getOrigenId());
        long entradasDestino = arcoRepository.countByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                procesoId, arco.getTipoDestino(), arco.getDestinoId());

        if (salidasOrigen == 1) {
            advertencias.add("El nodo de origen quedará sin camino de salida");
        }
        if (entradasDestino == 1) {
            advertencias.add("El nodo de destino quedará sin camino de entrada");
        }

        arco.setActivo(false);
        arcoRepository.save(arco);

        historialService.registrar(proceso, sesion, "Se eliminó el arco #" + arco.getId() + " " + describir(arco));

        return new EliminacionArcoResponseDTO("Arco eliminado correctamente", advertencias);
    }

    private Arco obtenerArco(Long arcoId, Long procesoId) {
        return arcoRepository.findByIdAndProcesoIdAndActivoTrue(arcoId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el arco indicado en este proceso"));
    }

    private void aplicarDatos(Arco arco, ArcoRequestDTO request, String condicion) {
        arco.setTipoOrigen(request.getTipoOrigen());
        arco.setOrigenId(request.getOrigenId());
        arco.setTipoDestino(request.getTipoDestino());
        arco.setDestinoId(request.getDestinoId());
        arco.setEtiqueta(request.getEtiqueta() == null || request.getEtiqueta().isBlank()
                ? null : request.getEtiqueta().trim());
        arco.setCondicion(condicion);
    }

    private String describir(Arco arco) {
        return "desde " + arco.getTipoOrigen() + " " + arco.getOrigenId()
                + " hacia " + arco.getTipoDestino() + " " + arco.getDestinoId();
    }

    // HU-11: origen y destino existen, son distintos, están en el mismo pool y no se repite el arco
    private void validarArco(Long procesoId, ArcoRequestDTO request, Long arcoIdActual) {
        if (request == null) {
            throw new OperacionInvalidaException("Los datos del arco son obligatorios");
        }
        if (request.getTipoOrigen() == null || request.getTipoDestino() == null
                || request.getOrigenId() == null || request.getDestinoId() == null) {
            throw new OperacionInvalidaException("Debe indicar el tipo y el ID de los nodos de origen y destino");
        }
        if (request.getOrigenId() <= 0 || request.getDestinoId() <= 0) {
            throw new OperacionInvalidaException("Los IDs de los nodos deben ser positivos");
        }
        if (request.getTipoOrigen() == request.getTipoDestino()
                && request.getOrigenId().equals(request.getDestinoId())) {
            throw new OperacionInvalidaException("El origen y el destino no pueden ser el mismo nodo");
        }

        Long poolOrigenId = obtenerPoolDeNodo(request.getTipoOrigen(), request.getOrigenId(), procesoId);
        Long poolDestinoId = obtenerPoolDeNodo(request.getTipoDestino(), request.getDestinoId(), procesoId);

        validarReglasEventosMensaje(request, procesoId);

        if (poolOrigenId == null || poolDestinoId == null) {
            throw new OperacionInvalidaException("Todo nodo conectado por un arco debe pertenecer a un pool");
        }
        if (!poolOrigenId.equals(poolDestinoId)) {
            throw new OperacionInvalidaException(
                    "El arco no puede conectar nodos de pools distintos. "
                            + "Para comunicar dos pools use un flujo de mensajes, no un arco.");
        }

        boolean duplicado = arcoIdActual == null
                ? arcoRepository.existsByProcesoIdAndTipoOrigenAndOrigenIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                        procesoId,
                        request.getTipoOrigen(), request.getOrigenId(),
                        request.getTipoDestino(), request.getDestinoId())
                : arcoRepository.existsByProcesoIdAndTipoOrigenAndOrigenIdAndTipoDestinoAndDestinoIdAndActivoTrueAndIdNot(
                        procesoId,
                        request.getTipoOrigen(), request.getOrigenId(),
                        request.getTipoDestino(), request.getDestinoId(),
                        arcoIdActual);

        if (duplicado) {
            throw new RecursoDuplicadoException("Ya existe un arco entre esos dos nodos");
        }
    }

    // HU-27: un Message Catch de inicio arranca el proceso, no puede recibir arcos
    private void validarReglasEventosMensaje(ArcoRequestDTO request, Long procesoId) {
        if (request.getTipoDestino() == TipoNodo.EVENTO) {
            EventoMensaje destino = obtenerEventoActivo(request.getDestinoId(), procesoId);
            if (destino.getTipo() == TipoEventoMensaje.CATCH_INICIO) {
                throw new OperacionInvalidaException("Un Message Catch de inicio no puede tener arcos entrantes");
            }
        }
    }

    /** Verifica que el nodo exista, esté activo y sea del proceso, y devuelve el id de su pool. */
    private Long obtenerPoolDeNodo(TipoNodo tipoNodo, Long nodoId, Long procesoId) {
        Pool pool = switch (tipoNodo) {
            case ACTIVIDAD -> obtenerActividadActiva(nodoId, procesoId).getLane().getPool();
            case GATEWAY -> obtenerGatewayActivo(nodoId, procesoId).getPool();
            case EVENTO -> obtenerEventoActivo(nodoId, procesoId).getPool();
        };
        return pool == null ? null : pool.getId();
    }

    // HU-14 / HU-15: exclusivo e inclusivo exigen condición; paralelo no la admite
    private String resolverCondicionGateway(Long procesoId, ArcoRequestDTO request) {
        if (request.getTipoOrigen() != TipoNodo.GATEWAY) {
            return null;
        }

        Gateway gateway = obtenerGatewayActivo(request.getOrigenId(), procesoId);
        String condicion = request.getCondicion();
        boolean tieneCondicion = condicion != null && !condicion.isBlank();

        if (gateway.getTipo() == TipoGateway.PARALELO) {
            if (tieneCondicion) {
                throw new OperacionInvalidaException(
                        "Los arcos salientes de un gateway PARALELO no deben tener condición");
            }
            return null;
        }

        if (!tieneCondicion) {
            throw new OperacionInvalidaException(
                    "Los arcos salientes de un gateway " + gateway.getTipo() + " deben tener una condición");
        }
        return condicion.trim();
    }

    private Actividad obtenerActividadActiva(Long actividadId, Long procesoId) {
        return actividadRepository.findByIdAndProcesoIdAndActivoTrue(actividadId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La actividad " + actividadId + " no existe, está eliminada o no pertenece al proceso"));
    }

    private Gateway obtenerGatewayActivo(Long gatewayId, Long procesoId) {
        return gatewayRepository.findByIdAndProcesoIdAndActivoTrue(gatewayId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El gateway " + gatewayId + " no existe, está eliminado o no pertenece al proceso"));
    }

    private EventoMensaje obtenerEventoActivo(Long eventoId, Long procesoId) {
        return eventoMensajeRepository.findByIdAndProcesoIdAndActivoTrue(eventoId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El evento " + eventoId + " no existe, está eliminado o no pertenece al proceso"));
    }
}
