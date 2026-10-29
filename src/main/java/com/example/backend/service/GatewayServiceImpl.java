package com.example.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EdicionGatewayResponseDTO;
import com.example.backend.dto.EliminacionGatewayResponseDTO;
import com.example.backend.dto.GatewayRequestDTO;
import com.example.backend.dto.GatewayResponseDTO;
import com.example.backend.entity.Arco;
import com.example.backend.entity.Gateway;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.TipoGateway;
import com.example.backend.entity.TipoNodo;
import com.example.backend.entity.TipoPool;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.GatewayMapper;
import com.example.backend.repository.ArcoRepository;
import com.example.backend.repository.GatewayRepository;
import com.example.backend.repository.PoolRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class GatewayServiceImpl implements GatewayService {

    private final GatewayRepository gatewayRepository;
    private final ArcoRepository arcoRepository;
    private final PoolRepository poolRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final GatewayMapper gatewayMapper;

    public GatewayServiceImpl(
            GatewayRepository gatewayRepository,
            ArcoRepository arcoRepository,
            PoolRepository poolRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            GatewayMapper gatewayMapper) {

        this.gatewayRepository = gatewayRepository;
        this.arcoRepository = arcoRepository;
        this.poolRepository = poolRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.gatewayMapper = gatewayMapper;
    }

    @Override
    public List<GatewayResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return gatewayRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(gatewayMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public GatewayResponseDTO crearGateway(Long procesoId, GatewayRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        validarRequest(request);
        Pool pool = obtenerPoolValido(request.getPoolId(), procesoId);

        Gateway gateway = new Gateway();
        gateway.setTipo(request.getTipo());
        gateway.setProceso(proceso);
        gateway.setPool(pool);
        gateway.setPosicionX(request.getPosicionX());
        gateway.setPosicionY(request.getPosicionY());
        gateway.setActivo(true);
        gateway = gatewayRepository.save(gateway);

        historialService.registrar(proceso, sesion,
                "Se creó el gateway #" + gateway.getId() + " de tipo " + gateway.getTipo());

        return gatewayMapper.toDTO(gateway);
    }

    // HU-15: cambiar el tipo cambia el significado del diagrama; se ajustan
    // las condiciones de los arcos salientes y se advierte lo que falte.
    @Override
    @Transactional
    public EdicionGatewayResponseDTO editarGateway(Long procesoId, Long gatewayId, GatewayRequestDTO request,
                                                   UsuarioSesion sesion) {
        validadorAcceso.exigirEdicion(sesion.rol());
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        validarRequest(request);
        Pool poolNuevo = obtenerPoolValido(request.getPoolId(), procesoId);

        Gateway gateway = obtenerGateway(gatewayId, procesoId);
        TipoGateway tipoAnterior = gateway.getTipo();

        validarCambioDePoolConArcos(procesoId, gatewayId, gateway.getPool(), poolNuevo);

        List<Arco> arcosSalientes = arcoRepository.findByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                procesoId, TipoNodo.GATEWAY, gatewayId);

        List<String> advertencias = ajustarArcosSalientes(request.getTipo(), arcosSalientes);

        gateway.setTipo(request.getTipo());
        gateway.setPool(poolNuevo);
        gateway.setPosicionX(request.getPosicionX());
        gateway.setPosicionY(request.getPosicionY());
        gateway = gatewayRepository.save(gateway);

        historialService.registrar(proceso, sesion,
                "Se editó el gateway #" + gateway.getId() + " de " + tipoAnterior + " a " + gateway.getTipo());

        return new EdicionGatewayResponseDTO(gatewayMapper.toDTO(gateway), advertencias);
    }

    // HU-16: elimina el gateway y sus arcos; advierte si la ramificación pierde su punto de decisión
    @Override
    @Transactional
    public EliminacionGatewayResponseDTO eliminarGateway(Long procesoId, Long gatewayId, boolean confirmar,
                                                         UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "eliminar gateways");
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Gateway gateway = obtenerGateway(gatewayId, procesoId);

        List<Arco> arcosSalientes = arcoRepository.findByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                procesoId, TipoNodo.GATEWAY, gatewayId);
        List<Arco> arcosConectados = new ArrayList<>(arcosSalientes);
        arcosConectados.addAll(arcoRepository.findByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                procesoId, TipoNodo.GATEWAY, gatewayId));

        boolean rompeRamificacion = arcosSalientes.size() >= 2;

        if (!confirmar) {
            String mensaje = "Debe confirmar la eliminación del gateway";
            if (!arcosConectados.isEmpty()) {
                mensaje += ". Se eliminarán " + arcosConectados.size() + " arco(s) conectado(s)";
            }
            if (rompeRamificacion) {
                mensaje += ". La ramificación quedará sin punto de decisión y el flujo puede perder coherencia";
            }
            throw new OperacionInvalidaException(mensaje);
        }

        List<String> advertencias = new ArrayList<>();
        if (rompeRamificacion) {
            advertencias.add("La ramificación quedó sin su punto de decisión y el flujo puede perder coherencia");
        }
        if (!arcosConectados.isEmpty()) {
            advertencias.add("Se eliminaron " + arcosConectados.size() + " arco(s) conectado(s) al gateway");
        }

        arcosConectados.forEach(arco -> arco.setActivo(false));
        arcoRepository.saveAll(arcosConectados);

        gateway.setActivo(false);
        gatewayRepository.save(gateway);

        historialService.registrar(proceso, sesion,
                "Se eliminó lógicamente el gateway #" + gateway.getId()
                        + " junto con " + arcosConectados.size() + " arco(s) conectado(s)");

        return new EliminacionGatewayResponseDTO(gateway.getId(), arcosConectados.size(), advertencias);
    }

    private List<String> ajustarArcosSalientes(TipoGateway tipoNuevo, List<Arco> arcosSalientes) {
        List<String> advertencias = new ArrayList<>();

        if (tipoNuevo == TipoGateway.PARALELO) {
            // En paralelo se siguen todos los caminos: las condiciones dejan de tener sentido
            if (!arcosSalientes.isEmpty()) {
                arcosSalientes.forEach(arco -> arco.setCondicion(null));
                arcoRepository.saveAll(arcosSalientes);
                advertencias.add("Se eliminaron las condiciones de los arcos salientes porque el gateway ahora es PARALELO");
            }
            return advertencias;
        }

        long sinCondicion = arcosSalientes.stream()
                .filter(arco -> arco.getCondicion() == null || arco.getCondicion().isBlank())
                .count();

        if (sinCondicion > 0) {
            advertencias.add("El gateway " + tipoNuevo + " tiene " + sinCondicion + " arco(s) saliente(s) sin condición");
        }
        if (arcosSalientes.size() < 2) {
            advertencias.add("Un gateway " + tipoNuevo + " de divergencia debe tener al menos dos arcos salientes");
        }
        if (tipoNuevo == TipoGateway.EXCLUSIVO) {
            advertencias.add("Verifique que las condiciones de los arcos salientes sean mutuamente excluyentes");
        }
        return advertencias;
    }

    private Gateway obtenerGateway(Long gatewayId, Long procesoId) {
        return gatewayRepository.findByIdAndProcesoIdAndActivoTrue(gatewayId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El gateway no existe, está eliminado o no pertenece al proceso"));
    }

    private void validarRequest(GatewayRequestDTO request) {
        if (request == null) {
            throw new OperacionInvalidaException("Los datos del gateway son obligatorios");
        }
        if (request.getTipo() == null) {
            throw new OperacionInvalidaException("El tipo de gateway es obligatorio");
        }
        if (request.getPoolId() == null) {
            throw new OperacionInvalidaException("Debe indicar el pool del gateway");
        }
    }

    private Pool obtenerPoolValido(Long poolId, Long procesoId) {
        Pool pool = poolRepository.findByIdAndProcesoIdAndActivoTrue(poolId, procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pool indicado no existe en este proceso"));

        if (pool.getTipo() == TipoPool.EXTERNO) {
            throw new OperacionInvalidaException("Un pool externo no puede contener gateways");
        }
        return pool;
    }

    // Los arcos no cruzan pools: un gateway conectado no puede cambiar de pool
    private void validarCambioDePoolConArcos(Long procesoId, Long gatewayId, Pool poolActual, Pool poolNuevo) {
        if (poolActual == null || poolActual.getId().equals(poolNuevo.getId())) {
            return;
        }

        boolean tieneArcos =
                !arcoRepository.findByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(
                        procesoId, TipoNodo.GATEWAY, gatewayId).isEmpty()
                || !arcoRepository.findByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                        procesoId, TipoNodo.GATEWAY, gatewayId).isEmpty();

        if (tieneArcos) {
            throw new OperacionInvalidaException(
                    "No se puede mover un gateway a otro pool mientras tenga arcos activos conectados");
        }
    }
}
