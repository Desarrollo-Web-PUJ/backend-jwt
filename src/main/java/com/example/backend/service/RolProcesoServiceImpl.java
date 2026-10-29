package com.example.backend.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.EliminacionRolProcesoResponseDTO;
import com.example.backend.dto.PaginaResponseDTO;
import com.example.backend.dto.ProcesoUsoDTO;
import com.example.backend.dto.RolProcesoListItemDTO;
import com.example.backend.dto.RolProcesoRequestDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.dto.UsoRolProcesoDTO;
import com.example.backend.entity.Empresa;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.RolProceso;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.RolProcesoMapper;
import com.example.backend.repository.EmpresaRepository;
import com.example.backend.repository.LaneRepository;
import com.example.backend.repository.ProcesoRepository;
import com.example.backend.repository.RolProcesoRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class RolProcesoServiceImpl implements RolProcesoService {

    private static final int LONGITUD_MAXIMA_NOMBRE = 80;
    private static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaRepository empresaRepository;
    private final LaneRepository laneRepository;
    private final ProcesoRepository procesoRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final RolProcesoMapper rolProcesoMapper;

    public RolProcesoServiceImpl(
            RolProcesoRepository rolProcesoRepository,
            EmpresaRepository empresaRepository,
            LaneRepository laneRepository,
            ProcesoRepository procesoRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            RolProcesoMapper rolProcesoMapper) {

        this.rolProcesoRepository = rolProcesoRepository;
        this.empresaRepository = empresaRepository;
        this.laneRepository = laneRepository;
        this.procesoRepository = procesoRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.rolProcesoMapper = rolProcesoMapper;
    }

    @Override
    @Transactional
    public RolProcesoResponseDTO crear(RolProcesoRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "crear roles de proceso");

        Empresa empresa = empresaRepository.findById(sesion.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa de la sesión no existe"));

        String nombre = normalizarNombre(request.getNombre());

        if (rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(sesion.empresaId(), nombre)) {
            throw new RecursoDuplicadoException("Ya existe un rol de proceso con ese nombre en la empresa");
        }

        RolProceso rolProceso = new RolProceso();
        rolProceso.setNombre(nombre);
        rolProceso.setDescripcion(normalizarDescripcion(request.getDescripcion()));
        rolProceso.setEmpresa(empresa);
        rolProceso.setActivo(true);

        return rolProcesoMapper.toDTO(rolProcesoRepository.save(rolProceso));
    }

    // HU-18: las lanes guardan la referencia al rol, así que renombrarlo
    // renombra todas las lanes que lo usan en todos los procesos.
    @Override
    @Transactional
    public RolProcesoResponseDTO editar(Long rolProcesoId, RolProcesoRequestDTO request, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "editar roles de proceso");

        RolProceso rolProceso = obtenerRolActivo(rolProcesoId, sesion.empresaId());

        String nombreNuevo = normalizarNombre(request.getNombre());
        String descripcionNueva = normalizarDescripcion(request.getDescripcion());

        if (rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(
                sesion.empresaId(), nombreNuevo, rolProcesoId)) {
            throw new RecursoDuplicadoException("Ya existe un rol de proceso con ese nombre en la empresa");
        }

        String nombreAnterior = rolProceso.getNombre();
        boolean cambioNombre = !nombreAnterior.equals(nombreNuevo);
        boolean cambioDescripcion = !Objects.equals(rolProceso.getDescripcion(), descripcionNueva);

        // Sin cambios reales no se escribe nada ni se ensucia el historial
        if (!cambioNombre && !cambioDescripcion) {
            return rolProcesoMapper.toDTO(rolProceso);
        }

        rolProceso.setNombre(nombreNuevo);
        rolProceso.setDescripcion(descripcionNueva);
        rolProceso = rolProcesoRepository.save(rolProceso);

        String descripcionHistorial = cambioNombre
                ? "Se renombró el rol de proceso '" + nombreAnterior + "' a '" + nombreNuevo + "'"
                        + (cambioDescripcion ? " y se actualizó su descripción" : "")
                : "Se actualizó la descripción del rol de proceso '" + nombreNuevo + "'";

        List<Long> procesoIds = obtenerUso(List.of(rolProcesoId))
                .getOrDefault(rolProcesoId, List.of())
                .stream()
                .map(ProcesoUsoDTO::getId)
                .toList();

        for (Proceso proceso : procesoRepository.findAllById(procesoIds)) {
            historialService.registrar(proceso, sesion, descripcionHistorial);
        }

        return rolProcesoMapper.toDTO(rolProceso);
    }

    // HU-19: un rol en uso no se elimina; se informa en qué procesos se usa
    @Override
    @Transactional
    public EliminacionRolProcesoResponseDTO eliminar(Long rolProcesoId, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "eliminar roles de proceso");

        RolProceso rolProceso = obtenerRolActivo(rolProcesoId, sesion.empresaId());

        List<ProcesoUsoDTO> procesosEnUso = obtenerUso(List.of(rolProcesoId))
                .getOrDefault(rolProcesoId, List.of());

        if (!procesosEnUso.isEmpty()) {
            String nombres = procesosEnUso.stream()
                    .map(ProcesoUsoDTO::getNombre)
                    .collect(Collectors.joining(", "));

            throw new OperacionInvalidaException(
                    "No se puede eliminar el rol de proceso '" + rolProceso.getNombre()
                            + "' porque está en uso en los procesos: " + nombres);
        }

        rolProceso.setActivo(false);
        rolProcesoRepository.save(rolProceso);

        return new EliminacionRolProcesoResponseDTO("Rol de proceso eliminado correctamente");
    }

    // HU-20: listado paginado con búsqueda, procesos donde se usa y si es eliminable
    @Override
    public PaginaResponseDTO<RolProcesoListItemDTO> consultar(String nombre, Pageable pageable, UsuarioSesion sesion) {
        String filtro = nombre == null ? "" : nombre.trim();

        Page<RolProceso> pagina = rolProcesoRepository.buscar(sesion.empresaId(), filtro, pageable);

        List<Long> ids = pagina.getContent().stream()
                .map(RolProceso::getId)
                .toList();

        Map<Long, List<ProcesoUsoDTO>> usoPorRol = ids.isEmpty() ? Map.of() : obtenerUso(ids);

        return PaginaResponseDTO.desde(pagina.map(rolProceso -> rolProcesoMapper.toListItemDTO(
                rolProceso,
                usoPorRol.getOrDefault(rolProceso.getId(), List.of()))));
    }

    /** Procesos en los que se usa cada rol, agrupados por id de rol. */
    private Map<Long, List<ProcesoUsoDTO>> obtenerUso(Collection<Long> rolIds) {
        return laneRepository.findUsoDeRoles(rolIds)
                .stream()
                .collect(Collectors.groupingBy(
                        UsoRolProcesoDTO::rolProcesoId,
                        Collectors.mapping(rolProcesoMapper::toProcesoUsoDTO, Collectors.toList())));
    }

    private RolProceso obtenerRolActivo(Long rolProcesoId, Long empresaId) {
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolProcesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El rol de proceso no existe en la empresa"));
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new OperacionInvalidaException("El nombre del rol de proceso es obligatorio");
        }

        String limpio = nombre.trim();
        if (limpio.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new OperacionInvalidaException(
                    "El nombre del rol de proceso no puede superar los " + LONGITUD_MAXIMA_NOMBRE + " caracteres");
        }
        return limpio;
    }

    private String normalizarDescripcion(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return null;
        }

        String limpia = descripcion.trim();
        if (limpia.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new OperacionInvalidaException(
                    "La descripción del rol de proceso no puede superar los "
                            + LONGITUD_MAXIMA_DESCRIPCION + " caracteres");
        }
        return limpia;
    }
}
