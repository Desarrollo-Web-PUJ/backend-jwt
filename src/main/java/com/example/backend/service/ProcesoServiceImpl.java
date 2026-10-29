package com.example.backend.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.HistorialProcesoDTO;
import com.example.backend.dto.PaginaResponseDTO;
import com.example.backend.dto.ProcesoCrearRequestDTO;
import com.example.backend.dto.ProcesoDetalleDTO;
import com.example.backend.dto.ProcesoEditarRequestDTO;
import com.example.backend.dto.ProcesoListItemDTO;
import com.example.backend.dto.ProcesoResponseDTO;
import com.example.backend.entity.Empresa;
import com.example.backend.entity.EstadoProceso;
import com.example.backend.entity.Proceso;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.ProcesoMapper;
import com.example.backend.repository.EmpresaRepository;
import com.example.backend.repository.ProcesoCompartidoRepository;
import com.example.backend.repository.ProcesoRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class ProcesoServiceImpl implements ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final EmpresaRepository empresaRepository;
    private final ProcesoCompartidoRepository procesoCompartidoRepository;
    private final PoolService poolService;
    private final DiagramaProcesoService diagramaProcesoService;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final ProcesoMapper procesoMapper;

    public ProcesoServiceImpl(
            ProcesoRepository procesoRepository,
            EmpresaRepository empresaRepository,
            ProcesoCompartidoRepository procesoCompartidoRepository,
            PoolService poolService,
            DiagramaProcesoService diagramaProcesoService,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            ProcesoMapper procesoMapper) {

        this.procesoRepository = procesoRepository;
        this.empresaRepository = empresaRepository;
        this.procesoCompartidoRepository = procesoCompartidoRepository;
        this.poolService = poolService;
        this.diagramaProcesoService = diagramaProcesoService;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.procesoMapper = procesoMapper;
    }

    @Override
    @Transactional
    public ProcesoResponseDTO crearProceso(UsuarioSesion sesion, ProcesoCrearRequestDTO request) {
        validadorAcceso.exigirEdicion(sesion.rol());

        String nombre = textoObligatorio(request.getNombre(), "El nombre del proceso es obligatorio");
        String categoria = textoObligatorio(request.getCategoria(), "La categoría es obligatoria");

        if (procesoRepository.existsByNombreAndEmpresaId(nombre, sesion.empresaId())) {
            throw new RecursoDuplicadoException("Ya existe un proceso con ese nombre en la empresa");
        }

        Empresa empresa = empresaRepository.findById(sesion.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa no existe"));

        Proceso proceso = new Proceso();
        proceso.setNombre(nombre);
        proceso.setDescripcion(textoOpcional(request.getDescripcion()));
        proceso.setCategoria(categoria);
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setEmpresa(empresa);
        proceso = procesoRepository.save(proceso);

        // HU-04 / HU-21: el proceso nace con el pool de la empresa ya definido
        poolService.crearPoolPropio(proceso);

        historialService.registrar(proceso, sesion, "Se creó el proceso '" + proceso.getNombre() + "'");

        return procesoMapper.toResponseDTO(proceso);
    }

    @Override
    @Transactional
    public ProcesoResponseDTO editarProceso(Long procesoId, UsuarioSesion sesion,
                                            ProcesoEditarRequestDTO request) {
        validadorAcceso.exigirEdicion(sesion.rol());

        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        String nombre = textoObligatorio(request.getNombre(), "El nombre del proceso es obligatorio");
        String categoria = textoObligatorio(request.getCategoria(), "La categoría es obligatoria");

        if (request.getEstado() == null) {
            throw new OperacionInvalidaException("El estado es obligatorio");
        }

        if (procesoRepository.existsByNombreAndEmpresaIdAndIdNot(nombre, sesion.empresaId(), procesoId)) {
            throw new RecursoDuplicadoException("Ya existe un proceso con ese nombre en la empresa");
        }

        proceso.setNombre(nombre);
        proceso.setDescripcion(textoOpcional(request.getDescripcion()));
        proceso.setCategoria(categoria);
        proceso.setEstado(request.getEstado());
        proceso = procesoRepository.save(proceso);

        historialService.registrar(proceso, sesion, "Se editó el proceso '" + proceso.getNombre() + "'");

        return procesoMapper.toResponseDTO(proceso);
    }

    // HU-06: la eliminación es lógica; el diagrama se conserva para auditoría
    @Override
    @Transactional
    public void eliminarProceso(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "eliminar procesos");

        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());
        proceso.setActivo(false);
        procesoRepository.save(proceso);

        historialService.registrar(proceso, sesion,
                "Se eliminó (lógicamente) el proceso '" + proceso.getNombre() + "'");
    }

    @Override
    public PaginaResponseDTO<ProcesoListItemDTO> listarProcesos(
            UsuarioSesion sesion,
            String nombre,
            EstadoProceso estado,
            String categoria,
            boolean incluirInactivos,
            Pageable pageable) {

        String nombreFiltro = nombre == null ? "" : nombre.trim();
        String categoriaFiltro = textoOpcional(categoria);

        return PaginaResponseDTO.desde(
                procesoRepository
                        .buscar(sesion.empresaId(), incluirInactivos, nombreFiltro, estado, categoriaFiltro, pageable)
                        .map(procesoMapper::toListItemDTO));
    }

    @Override
    public ProcesoDetalleDTO obtenerDetalle(Long procesoId, UsuarioSesion sesion) {
        Proceso proceso = obtenerProcesoPropioOCompartido(procesoId, sesion.empresaId());
        return diagramaProcesoService.construirDetalle(proceso);
    }

    @Override
    public List<HistorialProcesoDTO> obtenerHistorial(Long procesoId, UsuarioSesion sesion) {
        validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());
        return historialService.listarPorProceso(procesoId);
    }

    // HU-03 / HU-23: única excepción al aislamiento por empresa: los procesos
    // compartidos con la empresa del usuario, siempre en solo lectura.
    private Proceso obtenerProcesoPropioOCompartido(Long procesoId, Long empresaId) {
        return procesoRepository.findByIdAndEmpresaId(procesoId, empresaId)
                .orElseGet(() -> obtenerProcesoCompartido(procesoId, empresaId));
    }

    private Proceso obtenerProcesoCompartido(Long procesoId, Long empresaId) {
        boolean compartido = procesoCompartidoRepository
                .existsByProcesoIdAndEmpresaDestinoIdAndActivoTrue(procesoId, empresaId);

        if (!compartido) {
            throw new RecursoNoEncontradoException("El proceso no existe en la empresa indicada");
        }

        return procesoRepository.findById(procesoId)
                .filter(proceso -> Boolean.TRUE.equals(proceso.getActivo()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El proceso compartido no existe o ya no está activo"));
    }

    private String textoObligatorio(String valor, String mensajeError) {
        if (valor == null || valor.isBlank()) {
            throw new OperacionInvalidaException(mensajeError);
        }
        return valor.trim();
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
