package com.example.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.CompartirProcesoRequestDTO;
import com.example.backend.dto.ProcesoCompartidoResponseDTO;
import com.example.backend.entity.Empresa;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.ProcesoCompartido;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.mapper.ProcesoCompartidoMapper;
import com.example.backend.repository.EmpresaRepository;
import com.example.backend.repository.ProcesoCompartidoRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class ProcesoCompartidoServiceImpl implements ProcesoCompartidoService {

    private final ProcesoCompartidoRepository procesoCompartidoRepository;
    private final EmpresaRepository empresaRepository;
    private final HistorialService historialService;
    private final ValidadorAcceso validadorAcceso;
    private final ProcesoCompartidoMapper procesoCompartidoMapper;

    public ProcesoCompartidoServiceImpl(
            ProcesoCompartidoRepository procesoCompartidoRepository,
            EmpresaRepository empresaRepository,
            HistorialService historialService,
            ValidadorAcceso validadorAcceso,
            ProcesoCompartidoMapper procesoCompartidoMapper) {
        this.procesoCompartidoRepository = procesoCompartidoRepository;
        this.empresaRepository = empresaRepository;
        this.historialService = historialService;
        this.validadorAcceso = validadorAcceso;
        this.procesoCompartidoMapper = procesoCompartidoMapper;
    }

    @Override
    public List<ProcesoCompartidoResponseDTO> listarComparticiones(Long procesoId, UsuarioSesion sesion) {
        Proceso proceso = validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        return procesoCompartidoRepository.findByProcesoIdAndActivoTrue(proceso.getId())
                .stream()
                .map(procesoCompartidoMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProcesoCompartidoResponseDTO compartir(Long procesoId, CompartirProcesoRequestDTO request,
                                                  UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "compartir un proceso");
        Proceso proceso = validadorAcceso.obtenerProcesoActivo(procesoId, sesion.empresaId());

        Long empresaDestinoId = request.getEmpresaDestinoId();
        if (empresaDestinoId == null) {
            throw new OperacionInvalidaException("Debe indicar la empresa destino");
        }
        if (empresaDestinoId.equals(sesion.empresaId())) {
            throw new OperacionInvalidaException("No se puede compartir un proceso con la misma empresa propietaria");
        }

        Empresa empresaDestino = empresaRepository.findById(empresaDestinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa destino no existe"));

        // Si ya se compartió antes y luego se retiró, se reactiva el mismo registro
        ProcesoCompartido compartido = procesoCompartidoRepository
                .findByProcesoIdAndEmpresaDestinoId(proceso.getId(), empresaDestino.getId())
                .orElseGet(ProcesoCompartido::new);

        if (Boolean.TRUE.equals(compartido.getActivo()) && compartido.getId() != null) {
            throw new RecursoDuplicadoException("El proceso ya está compartido con esa empresa");
        }

        compartido.setProceso(proceso);
        compartido.setEmpresaDestino(empresaDestino);
        compartido.setFechaCompartido(LocalDateTime.now());
        compartido.setActivo(true);
        compartido = procesoCompartidoRepository.save(compartido);

        historialService.registrar(proceso, sesion,
                "Se compartió el proceso (solo lectura) con la empresa \"" + empresaDestino.getNombre() + "\"");

        return procesoCompartidoMapper.toDTO(compartido);
    }

    @Override
    @Transactional
    public void dejarDeCompartir(Long procesoId, Long empresaDestinoId, UsuarioSesion sesion) {
        validadorAcceso.exigirAdministrador(sesion.rol(), "modificar la compartición de un proceso");
        Proceso proceso = validadorAcceso.obtenerProceso(procesoId, sesion.empresaId());

        ProcesoCompartido compartido = procesoCompartidoRepository
                .findByProcesoIdAndEmpresaDestinoIdAndActivoTrue(proceso.getId(), empresaDestinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proceso no está compartido con esa empresa"));

        compartido.setActivo(false);
        procesoCompartidoRepository.save(compartido);

        historialService.registrar(proceso, sesion,
                "Se dejó de compartir el proceso con la empresa \"" + compartido.getEmpresaDestino().getNombre() + "\"");
    }

    @Override
    public List<ProcesoCompartidoResponseDTO> listarCompartidosConmigo(UsuarioSesion sesion) {
        return procesoCompartidoRepository.findByEmpresaDestinoIdAndActivoTrue(sesion.empresaId())
                .stream()
                .filter(compartido -> Boolean.TRUE.equals(compartido.getProceso().getActivo()))
                .map(procesoCompartidoMapper::toDTO)
                .toList();
    }
}
