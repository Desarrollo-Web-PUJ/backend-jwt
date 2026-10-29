package com.example.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.ActividadDTO;
import com.example.backend.dto.ActividadRequestDTO;
import com.example.backend.dto.EliminacionActividadResponseDTO;
import com.example.backend.entity.Actividad;
import com.example.backend.entity.Arco;
import com.example.backend.entity.Lane;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.TipoNodo;
import com.example.backend.entity.TipoPool;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.ActividadMapper;
import com.example.backend.repository.ActividadRepository;
import com.example.backend.repository.ArcoRepository;
import com.example.backend.repository.LaneRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class ActividadServiceImpl implements ActividadService {

    private final ActividadRepository actividadRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final ActividadMapper actividadMapper;

    public ActividadServiceImpl(
            ActividadRepository actividadRepository,
            LaneRepository laneRepository,
            ArcoRepository arcoRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            ActividadMapper actividadMapper) {

        this.actividadRepository = actividadRepository;
        this.laneRepository = laneRepository;
        this.arcoRepository = arcoRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.actividadMapper = actividadMapper;
    }

    @Override
    public List<ActividadDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return actividadRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(actividadMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ActividadDTO crearActividad(Long procesoId, ActividadRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        validarDatosActividad(request);

        String nombre = request.getNombre().trim();
        if (actividadRepository.existsByNombreAndProcesoId(nombre, procesoId)) {
            throw new RecursoDuplicadoException("Ya existe una actividad con ese nombre en este proceso");
        }

        Lane lane = obtenerLaneDelProceso(request.getLaneId(), procesoId);

        Actividad actividad = new Actividad();
        actividad.setNombre(nombre);
        actividad.setTipo(request.getTipo());
        actividad.setProceso(proceso);
        actividad.setLane(lane);
        actividad.setPosicionX(request.getPosicionX());
        actividad.setPosicionY(request.getPosicionY());
        actividad.setActivo(true);
        actividad = actividadRepository.save(actividad);

        historialService.registrar(proceso, sesion,
                "Se creó la actividad '" + actividad.getNombre() + "' en la lane '" + lane.getNombre() + "'");

        return actividadMapper.toDTO(actividad);
    }

    // HU-09: cambiar de lane cambia el responsable; los arcos conectados se conservan
    @Override
    @Transactional
    public ActividadDTO editarActividad(Long procesoId, Long actividadId, ActividadRequestDTO request,
                                        UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Actividad actividad = obtenerActividad(actividadId, procesoId);
        validarDatosActividad(request);

        String nombre = request.getNombre().trim();
        if (actividadRepository.existsByNombreAndProcesoIdAndIdNot(nombre, procesoId, actividadId)) {
            throw new RecursoDuplicadoException("Ya existe una actividad con ese nombre en este proceso");
        }

        Lane laneNueva = obtenerLaneDelProceso(request.getLaneId(), procesoId);
        validarCambioDePoolConArcos(procesoId, actividadId, actividad.getLane(), laneNueva);

        String nombreAnterior = actividad.getNombre();
        String laneAnterior = actividad.getLane().getNombre();

        actividad.setNombre(nombre);
        actividad.setTipo(request.getTipo());
        actividad.setLane(laneNueva);
        actividad.setPosicionX(request.getPosicionX());
        actividad.setPosicionY(request.getPosicionY());
        actividad = actividadRepository.save(actividad);

        String descripcion = "Se editó la actividad #" + actividad.getId()
                + " ('" + nombreAnterior + "' -> '" + actividad.getNombre() + "')";
        if (!laneAnterior.equals(laneNueva.getNombre())) {
            descripcion += " y cambió de lane '" + laneAnterior + "' a '" + laneNueva.getNombre() + "'";
        }
        historialService.registrar(proceso, sesion, descripcion);

        return actividadMapper.toDTO(actividad);
    }

    // HU-10: elimina también los arcos conectados y advierte si el flujo queda partido
    @Override
    @Transactional
    public EliminacionActividadResponseDTO eliminarActividad(Long procesoId, Long actividadId, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "eliminar actividades");
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Actividad actividad = obtenerActividad(actividadId, procesoId);
        List<Arco> arcosConectados = arcosConectados(procesoId, actividadId);
        Set<String> advertencias = advertenciasPorArcosEliminados(procesoId, actividadId, arcosConectados);

        if (!arcosConectados.isEmpty()) {
            arcosConectados.forEach(arco -> arco.setActivo(false));
            arcoRepository.saveAll(arcosConectados);
            advertencias.add("Se eliminaron " + arcosConectados.size() + " arco(s) conectado(s) a la actividad");
        }

        actividad.setActivo(false);
        actividadRepository.save(actividad);

        historialService.registrar(proceso, sesion,
                "Se eliminó la actividad #" + actividad.getId() + " '" + actividad.getNombre() + "'"
                        + (arcosConectados.isEmpty() ? "" : " junto con " + arcosConectados.size() + " arco(s) conectado(s)"));

        return new EliminacionActividadResponseDTO("Actividad eliminada correctamente", new ArrayList<>(advertencias));
    }

    private Actividad obtenerActividad(Long actividadId, Long procesoId) {
        return actividadRepository.findByIdAndProcesoIdAndActivoTrue(actividadId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La actividad no existe en este proceso"));
    }

    private List<Arco> arcosConectados(Long procesoId, Long actividadId) {
        List<Arco> arcos = new ArrayList<>();
        arcos.addAll(arcoRepository.findByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                procesoId, TipoNodo.ACTIVIDAD, actividadId));
        arcos.addAll(arcoRepository.findByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                procesoId, TipoNodo.ACTIVIDAD, actividadId));
        return arcos;
    }

    /**
     * Revisa los vecinos de la actividad: si todos los arcos de entrada (o de salida)
     * de un nodo se van a eliminar, ese nodo queda desconectado.
     */
    private Set<String> advertenciasPorArcosEliminados(Long procesoId, Long actividadId, List<Arco> arcosConectados) {
        Set<String> advertencias = new LinkedHashSet<>();

        for (Arco arco : arcosConectados) {
            boolean saleDeLaActividad = arco.getTipoOrigen() == TipoNodo.ACTIVIDAD
                    && actividadId.equals(arco.getOrigenId());

            if (saleDeLaActividad) {
                long entradasDestino = arcoRepository.countByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                        procesoId, arco.getTipoDestino(), arco.getDestinoId());
                long entradasQueSeEliminan = arcosConectados.stream()
                        .filter(otro -> otro.getTipoDestino() == arco.getTipoDestino()
                                && Objects.equals(otro.getDestinoId(), arco.getDestinoId()))
                        .count();
                if (entradasDestino == entradasQueSeEliminan) {
                    advertencias.add("El nodo " + arco.getTipoDestino() + " " + arco.getDestinoId()
                            + " quedará sin camino de entrada");
                }
            } else {
                long salidasOrigen = arcoRepository.countByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                        procesoId, arco.getTipoOrigen(), arco.getOrigenId());
                long salidasQueSeEliminan = arcosConectados.stream()
                        .filter(otro -> otro.getTipoOrigen() == arco.getTipoOrigen()
                                && Objects.equals(otro.getOrigenId(), arco.getOrigenId()))
                        .count();
                if (salidasOrigen == salidasQueSeEliminan) {
                    advertencias.add("El nodo " + arco.getTipoOrigen() + " " + arco.getOrigenId()
                            + " quedará sin camino de salida");
                }
            }
        }
        return advertencias;
    }

    private void validarDatosActividad(ActividadRequestDTO request) {
        if (request == null) {
            throw new OperacionInvalidaException("Los datos de la actividad son obligatorios");
        }
        if (request.getNombre() == null || request.getNombre().isBlank()) {
            throw new OperacionInvalidaException("El nombre de la actividad es obligatorio");
        }
        if (request.getTipo() == null) {
            throw new OperacionInvalidaException("El tipo de actividad es obligatorio");
        }
        if (request.getLaneId() == null) {
            throw new OperacionInvalidaException("Debe seleccionar la lane responsable");
        }
    }

    // HU-08 / HU-22: toda actividad pertenece a una lane activa, con rol, de un pool propio
    private Lane obtenerLaneDelProceso(Long laneId, Long procesoId) {
        Lane lane = laneRepository.findById(laneId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La lane no existe"));

        if (!lane.getProceso().getId().equals(procesoId)) {
            throw new OperacionInvalidaException("La lane seleccionada no pertenece a este proceso");
        }
        if (!Boolean.TRUE.equals(lane.getActivo())) {
            throw new OperacionInvalidaException("La lane seleccionada está eliminada");
        }
        if (lane.getRolProceso() == null) {
            throw new OperacionInvalidaException("La lane seleccionada no tiene rol de proceso asociado");
        }

        Pool pool = lane.getPool();
        if (pool == null || !Boolean.TRUE.equals(pool.getActivo())) {
            throw new OperacionInvalidaException("La lane seleccionada no pertenece a un pool activo");
        }
        if (pool.getTipo() == TipoPool.EXTERNO) {
            throw new OperacionInvalidaException("No se pueden crear actividades dentro de un pool externo");
        }
        return lane;
    }

    // Los arcos no cruzan pools: una actividad conectada no puede cambiar de pool
    private void validarCambioDePoolConArcos(Long procesoId, Long actividadId, Lane laneActual, Lane laneNueva) {
        if (laneActual.getPool() == null) {
            throw new OperacionInvalidaException("La lane actual de la actividad no pertenece a un pool");
        }

        boolean cambiaDePool = !laneActual.getPool().getId().equals(laneNueva.getPool().getId());

        if (cambiaDePool && !arcosConectados(procesoId, actividadId).isEmpty()) {
            throw new OperacionInvalidaException(
                    "No se puede mover una actividad a otro pool mientras tenga arcos activos conectados");
        }
    }
}
