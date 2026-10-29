package com.example.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EliminacionPoolResponseDTO;
import com.example.backend.dto.PoolRequestDTO;
import com.example.backend.dto.PoolResponseDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.TipoPool;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.PoolMapper;
import com.example.backend.mapper.RolProcesoMapper;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.GatewayRepository;
import com.example.backend.repository.LaneRepository;
import com.example.backend.repository.PoolRepository;
import com.example.backend.repository.RolProcesoRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class PoolServiceImpl implements PoolService {

    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final GatewayRepository gatewayRepository;
    private final EventoMensajeRepository eventoMensajeRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final PoolMapper poolMapper;
    private final RolProcesoMapper rolProcesoMapper;

    public PoolServiceImpl(
            PoolRepository poolRepository,
            LaneRepository laneRepository,
            GatewayRepository gatewayRepository,
            EventoMensajeRepository eventoMensajeRepository,
            RolProcesoRepository rolProcesoRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            PoolMapper poolMapper,
            RolProcesoMapper rolProcesoMapper) {
        this.poolRepository = poolRepository;
        this.laneRepository = laneRepository;
        this.gatewayRepository = gatewayRepository;
        this.eventoMensajeRepository = eventoMensajeRepository;
        this.rolProcesoRepository = rolProcesoRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.poolMapper = poolMapper;
        this.rolProcesoMapper = rolProcesoMapper;
    }

    @Override
    public List<PoolResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return poolRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<RolProcesoResponseDTO> listarRolesDisponibles(Long procesoId, Long poolId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());
        obtenerPool(poolId, procesoId);

        return rolProcesoRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(sesion.empresaId())
                .stream()
                .map(rolProcesoMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public PoolResponseDTO crearPool(Long procesoId, PoolRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        validarRequest(request);

        String nombre = request.getNombre().trim();

        if (poolRepository.existsByProcesoIdAndNombreIgnoreCaseAndActivoTrue(procesoId, nombre)) {
            throw new RecursoDuplicadoException("Ya existe un pool con ese nombre en este proceso");
        }

        if (request.getTipo() == TipoPool.PROPIO
                && poolRepository.existsByProcesoIdAndTipoAndActivoTrue(procesoId, TipoPool.PROPIO)) {
            throw new OperacionInvalidaException("El proceso ya tiene definido el pool propio de la empresa");
        }

        Pool pool = poolRepository.save(nuevoPool(proceso, nombre, request.getTipo()));

        historialService.registrar(proceso, sesion,
                "Se creó el pool \"" + pool.getNombre() + "\" (" + pool.getTipo() + ")");

        return toDTO(pool);
    }

    @Override
    @Transactional
    public PoolResponseDTO editarPool(Long procesoId, Long poolId, PoolRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        validarRequest(request);

        Pool pool = obtenerPool(poolId, procesoId);
        String nombre = request.getNombre().trim();
        TipoPool tipoNuevo = request.getTipo();

        if (poolRepository.existsByProcesoIdAndNombreIgnoreCaseAndIdNotAndActivoTrue(procesoId, nombre, poolId)) {
            throw new RecursoDuplicadoException("Ya existe otro pool con ese nombre en este proceso");
        }

        if (pool.getTipo() == TipoPool.PROPIO
                && tipoNuevo != TipoPool.PROPIO
                && poolRepository.countByProcesoIdAndTipoAndActivoTrue(procesoId, TipoPool.PROPIO) <= 1) {
            throw new OperacionInvalidaException("El proceso debe conservar su pool propio de empresa");
        }

        if (pool.getTipo() != TipoPool.PROPIO
                && tipoNuevo == TipoPool.PROPIO
                && poolRepository.existsByProcesoIdAndTipoAndActivoTrue(procesoId, TipoPool.PROPIO)) {
            throw new OperacionInvalidaException("El proceso ya tiene definido el pool propio de la empresa");
        }

        // HU-21: un pool externo es caja negra, no puede tener elementos internos
        if (tipoNuevo == TipoPool.EXTERNO && laneRepository.countByPoolIdAndActivoTrue(poolId) > 0) {
            throw new OperacionInvalidaException(
                    "No se puede marcar como externo (caja negra) un pool que ya tiene lanes definidas");
        }

        pool.setNombre(nombre);
        pool.setTipo(tipoNuevo);
        pool = poolRepository.save(pool);

        historialService.registrar(proceso, sesion,
                "Se editó el pool #" + pool.getId() + " (\"" + pool.getNombre() + "\")");

        return toDTO(pool);
    }

    @Override
    @Transactional
    public EliminacionPoolResponseDTO eliminarPool(Long procesoId, Long poolId, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "eliminar pools");
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Pool pool = obtenerPool(poolId, procesoId);

        if (laneRepository.countByPoolIdAndActivoTrue(poolId) > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar el pool: primero elimine o reasigne sus lanes");
        }
        if (gatewayRepository.existsByPoolIdAndActivoTrue(poolId)) {
            throw new OperacionInvalidaException("No se puede eliminar el pool: primero elimine sus gateways");
        }
        if (eventoMensajeRepository.existsByPoolIdAndActivoTrue(poolId)) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar el pool: primero elimine sus eventos de mensaje");
        }
        if (pool.getTipo() == TipoPool.PROPIO
                && poolRepository.countByProcesoIdAndTipoAndActivoTrue(procesoId, TipoPool.PROPIO) <= 1) {
            throw new OperacionInvalidaException("No se puede eliminar el pool propio de la empresa");
        }

        pool.setActivo(false);
        poolRepository.save(pool);

        historialService.registrar(proceso, sesion,
                "Se eliminó el pool #" + pool.getId() + " (\"" + pool.getNombre() + "\")");

        return new EliminacionPoolResponseDTO("Pool eliminado correctamente", List.of());
    }

    @Override
    @Transactional
    public void crearPoolPropio(Proceso proceso) {
        poolRepository.save(nuevoPool(proceso, proceso.getEmpresa().getNombre(), TipoPool.PROPIO));
    }

    private Pool nuevoPool(Proceso proceso, String nombre, TipoPool tipo) {
        Pool pool = new Pool();
        pool.setNombre(nombre);
        pool.setTipo(tipo);
        pool.setProceso(proceso);
        pool.setActivo(true);
        return pool;
    }

    private void validarRequest(PoolRequestDTO request) {
        if (request == null || request.getNombre() == null || request.getNombre().isBlank()) {
            throw new OperacionInvalidaException("El nombre del pool es obligatorio");
        }
        if (request.getTipo() == null) {
            throw new OperacionInvalidaException("El tipo de pool es obligatorio");
        }
    }

    private Pool obtenerPool(Long poolId, Long procesoId) {
        return poolRepository.findByIdAndProcesoIdAndActivoTrue(poolId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pool indicado en este proceso"));
    }

    private PoolResponseDTO toDTO(Pool pool) {
        return poolMapper.toDTO(pool, laneRepository.countByPoolIdAndActivoTrue(pool.getId()));
    }
}
