package com.example.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EliminacionLaneResponseDTO;
import com.example.backend.dto.LaneDTO;
import com.example.backend.dto.LaneRequestDTO;
import com.example.backend.dto.ReordenarLanesRequestDTO;
import com.example.backend.entity.Lane;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.RolProceso;
import com.example.backend.entity.TipoPool;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.PermisoDenegadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.LaneMapper;
import com.example.backend.repository.ActividadRepository;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.LaneRepository;
import com.example.backend.repository.PoolRepository;
import com.example.backend.repository.RolProcesoRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class LaneServiceImpl implements LaneService {

    private final LaneRepository laneRepository;
    private final PoolRepository poolRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final ActividadRepository actividadRepository;
    private final EventoMensajeRepository eventoMensajeRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final LaneMapper laneMapper;

    public LaneServiceImpl(
            LaneRepository laneRepository,
            PoolRepository poolRepository,
            RolProcesoRepository rolProcesoRepository,
            ActividadRepository actividadRepository,
            EventoMensajeRepository eventoMensajeRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            LaneMapper laneMapper) {
        this.laneRepository = laneRepository;
        this.poolRepository = poolRepository;
        this.rolProcesoRepository = rolProcesoRepository;
        this.actividadRepository = actividadRepository;
        this.eventoMensajeRepository = eventoMensajeRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.laneMapper = laneMapper;
    }

    @Override
    public List<LaneDTO> listarPorPool(Long procesoId, Long poolId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());
        obtenerPool(poolId, procesoId);
        return listarDTO(poolId);
    }

    @Override
    @Transactional
    public LaneDTO crearLane(Long procesoId, Long poolId, LaneRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        Pool pool = obtenerPoolPropio(poolId, procesoId);
        RolProceso rolProceso = obtenerRolProcesoActivo(request.getRolProcesoId(), sesion.empresaId());

        if (laneRepository.existsByPoolIdAndRolProcesoIdAndActivoTrue(poolId, rolProceso.getId())) {
            throw new OperacionInvalidaException("Ese rol de proceso ya tiene una lane en este pool");
        }

        Lane lane = new Lane();
        lane.setNombre(rolProceso.getNombre());
        lane.setProceso(proceso);
        lane.setPool(pool);
        lane.setRolProceso(rolProceso);
        lane.setOrden(Math.toIntExact(laneRepository.countByPoolIdAndActivoTrue(poolId)));
        lane.setActivo(true);
        lane = laneRepository.save(lane);

        if (request.getOrden() != null) {
            moverLane(poolId, lane, request.getOrden());
        }

        historialService.registrar(proceso, sesion,
                "Se creó la lane '" + lane.getNombre() + "' en el pool '" + pool.getNombre() + "'");

        return laneMapper.toDTO(lane);
    }

    @Override
    @Transactional
    public LaneDTO editarLane(Long procesoId, Long poolId, Long laneId, LaneRequestDTO request,
                              UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        obtenerPoolPropio(poolId, procesoId);

        Lane lane = obtenerLane(laneId, poolId);
        RolProceso rolNuevo = obtenerRolProcesoActivo(request.getRolProcesoId(), sesion.empresaId());

        Long rolActualId = lane.getRolProceso() != null ? lane.getRolProceso().getId() : null;
        boolean cambiaDeRol = !rolNuevo.getId().equals(rolActualId);

        if (cambiaDeRol && laneRepository.existsByPoolIdAndRolProcesoIdAndActivoTrue(poolId, rolNuevo.getId())) {
            throw new OperacionInvalidaException("Ese rol de proceso ya tiene una lane en este pool");
        }

        String nombreAnterior = lane.getNombre();
        lane.setRolProceso(rolNuevo);
        lane.setNombre(rolNuevo.getNombre());
        lane = laneRepository.save(lane);

        if (request.getOrden() != null) {
            moverLane(poolId, lane, request.getOrden());
        }

        historialService.registrar(proceso, sesion,
                "Se editó la lane '" + nombreAnterior + "' (ahora '" + lane.getNombre() + "')");

        return laneMapper.toDTO(lane);
    }

    @Override
    @Transactional
    public List<LaneDTO> reordenarLanes(Long procesoId, Long poolId, ReordenarLanesRequestDTO request,
                                        UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        Pool pool = obtenerPoolPropio(poolId, procesoId);

        List<Long> idsEnOrden = request.getLaneIdsEnOrden();
        List<Lane> lanes = laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAsc(poolId);

        if (idsEnOrden == null || lanes.size() != idsEnOrden.size()) {
            throw new OperacionInvalidaException("Debe indicar el orden de todas las lanes del pool");
        }
        Set<Long> idsUnicos = new HashSet<>(idsEnOrden);
        if (idsUnicos.size() != idsEnOrden.size()) {
            throw new OperacionInvalidaException("El orden de lanes no puede contener IDs repetidos");
        }

        Map<Long, Lane> lanesPorId = lanes.stream()
                .collect(Collectors.toMap(Lane::getId, Function.identity()));

        for (int posicion = 0; posicion < idsEnOrden.size(); posicion++) {
            Long laneId = idsEnOrden.get(posicion);
            Lane lane = lanesPorId.get(laneId);
            if (lane == null) {
                throw new OperacionInvalidaException("La lane " + laneId + " no pertenece a este pool");
            }
            lane.setOrden(posicion);
        }
        laneRepository.saveAll(lanes);

        historialService.registrar(proceso, sesion, "Se reordenaron las lanes del pool '" + pool.getNombre() + "'");

        return listarDTO(poolId);
    }

    // HU-22: no se elimina una lane que contenga elementos; primero se reasignan
    @Override
    @Transactional
    public EliminacionLaneResponseDTO eliminarLane(Long procesoId, Long poolId, Long laneId, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        obtenerPool(poolId, procesoId);

        Lane lane = obtenerLane(laneId, poolId);

        if (actividadRepository.existsByLaneIdAndActivoTrue(laneId)) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar la lane: reasigne sus actividades a otra lane primero");
        }
        if (eventoMensajeRepository.existsByLaneIdAndActivoTrue(laneId)) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar la lane: reasigne o elimine sus eventos de mensaje primero");
        }

        lane.setActivo(false);
        laneRepository.save(lane);
        compactarOrden(poolId);

        historialService.registrar(proceso, sesion, "Se eliminó la lane '" + lane.getNombre() + "'");

        return new EliminacionLaneResponseDTO("Lane eliminada correctamente", List.of());
    }

    private List<LaneDTO> listarDTO(Long poolId) {
        return laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAsc(poolId)
                .stream()
                .map(laneMapper::toDTO)
                .toList();
    }

    private RolProceso obtenerRolProcesoActivo(Long rolProcesoId, Long empresaId) {
        if (rolProcesoId == null) {
            throw new OperacionInvalidaException("Debe indicar el rol de proceso de la lane");
        }

        RolProceso rolProceso = rolProcesoRepository.findById(rolProcesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El rol de proceso indicado no existe"));

        if (!rolProceso.getEmpresa().getId().equals(empresaId)) {
            throw new PermisoDenegadoException("El rol de proceso no pertenece a esta empresa");
        }
        if (!Boolean.TRUE.equals(rolProceso.getActivo())) {
            throw new OperacionInvalidaException("El rol de proceso indicado está eliminado");
        }
        return rolProceso;
    }

    private Pool obtenerPool(Long poolId, Long procesoId) {
        return poolRepository.findByIdAndProcesoIdAndActivoTrue(poolId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pool indicado en este proceso"));
    }

    private Pool obtenerPoolPropio(Long poolId, Long procesoId) {
        Pool pool = obtenerPool(poolId, procesoId);
        if (pool.getTipo() == TipoPool.EXTERNO) {
            throw new OperacionInvalidaException(
                    "Un pool externo (caja negra) no puede tener lanes ni elementos internos");
        }
        return pool;
    }

    private Lane obtenerLane(Long laneId, Long poolId) {
        return laneRepository.findByIdAndPoolIdAndActivoTrue(laneId, poolId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la lane indicada en este pool"));
    }

    /** Ubica la lane en la posición pedida y renumera las demás de forma consecutiva. */
    private void moverLane(Long poolId, Lane laneMovida, int posicionSolicitada) {
        List<Lane> lanes = new ArrayList<>(laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAsc(poolId));
        lanes.removeIf(lane -> lane.getId().equals(laneMovida.getId()));

        int posicion = Math.max(0, Math.min(posicionSolicitada, lanes.size()));
        lanes.add(posicion, laneMovida);
        asignarOrdenConsecutivo(lanes);
    }

    private void compactarOrden(Long poolId) {
        asignarOrdenConsecutivo(laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAsc(poolId));
    }

    private void asignarOrdenConsecutivo(List<Lane> lanes) {
        for (int i = 0; i < lanes.size(); i++) {
            lanes.get(i).setOrden(i);
        }
        laneRepository.saveAll(lanes);
    }
}
