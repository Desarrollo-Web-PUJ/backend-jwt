package com.example.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.HistorialProcesoDTO;
import com.example.backend.entity.HistorialProceso;
import com.example.backend.entity.Proceso;
import com.example.backend.mapper.HistorialProcesoMapper;
import com.example.backend.repository.HistorialProcesoRepository;
import com.example.backend.sesion.UsuarioSesion;

@Service
@Transactional(readOnly = true)
public class HistorialServiceImpl implements HistorialService {

    private final HistorialProcesoRepository historialRepository;
    private final HistorialProcesoMapper historialMapper;

    public HistorialServiceImpl(
            HistorialProcesoRepository historialRepository,
            HistorialProcesoMapper historialMapper) {
        this.historialRepository = historialRepository;
        this.historialMapper = historialMapper;
    }

    @Override
    @Transactional
    public void registrar(Proceso proceso, UsuarioSesion autor, String descripcion) {
        historialRepository.save(
                new HistorialProceso(proceso, descripcion, autor.nombre(), LocalDateTime.now()));
    }

    @Override
    public List<HistorialProcesoDTO> listarPorProceso(Long procesoId) {
        return historialRepository.findByProcesoIdOrderByFechaDesc(procesoId)
                .stream()
                .map(historialMapper::toDTO)
                .toList();
    }
}
