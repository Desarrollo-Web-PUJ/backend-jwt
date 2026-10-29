package com.example.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.ActividadDTO;
import com.example.backend.dto.ArcoResponseDTO;
import com.example.backend.dto.EventoMensajeResponseDTO;
import com.example.backend.dto.FlujoMensajeResponseDTO;
import com.example.backend.dto.GatewayResponseDTO;
import com.example.backend.dto.LaneDTO;
import com.example.backend.dto.PoolResponseDTO;
import com.example.backend.dto.ProcesoDetalleDTO;
import com.example.backend.entity.Lane;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.mapper.ActividadMapper;
import com.example.backend.mapper.ArcoMapper;
import com.example.backend.mapper.EventoMensajeMapper;
import com.example.backend.mapper.FlujoMensajeMapper;
import com.example.backend.mapper.GatewayMapper;
import com.example.backend.mapper.LaneMapper;
import com.example.backend.mapper.PoolMapper;
import com.example.backend.repository.ActividadRepository;
import com.example.backend.repository.ArcoRepository;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.FlujoMensajeRepository;
import com.example.backend.repository.GatewayRepository;
import com.example.backend.repository.LaneRepository;
import com.example.backend.repository.PoolRepository;

@Service
@Transactional(readOnly = true)
public class DiagramaProcesoServiceImpl implements DiagramaProcesoService {

    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final ArcoRepository arcoRepository;
    private final EventoMensajeRepository eventoMensajeRepository;
    private final FlujoMensajeRepository flujoMensajeRepository;

    private final PoolMapper poolMapper;
    private final LaneMapper laneMapper;
    private final ActividadMapper actividadMapper;
    private final GatewayMapper gatewayMapper;
    private final ArcoMapper arcoMapper;
    private final EventoMensajeMapper eventoMensajeMapper;
    private final FlujoMensajeMapper flujoMensajeMapper;

    public DiagramaProcesoServiceImpl(
            PoolRepository poolRepository,
            LaneRepository laneRepository,
            ActividadRepository actividadRepository,
            GatewayRepository gatewayRepository,
            ArcoRepository arcoRepository,
            EventoMensajeRepository eventoMensajeRepository,
            FlujoMensajeRepository flujoMensajeRepository,
            PoolMapper poolMapper,
            LaneMapper laneMapper,
            ActividadMapper actividadMapper,
            GatewayMapper gatewayMapper,
            ArcoMapper arcoMapper,
            EventoMensajeMapper eventoMensajeMapper,
            FlujoMensajeMapper flujoMensajeMapper) {

        this.poolRepository = poolRepository;
        this.laneRepository = laneRepository;
        this.actividadRepository = actividadRepository;
        this.gatewayRepository = gatewayRepository;
        this.arcoRepository = arcoRepository;
        this.eventoMensajeRepository = eventoMensajeRepository;
        this.flujoMensajeRepository = flujoMensajeRepository;
        this.poolMapper = poolMapper;
        this.laneMapper = laneMapper;
        this.actividadMapper = actividadMapper;
        this.gatewayMapper = gatewayMapper;
        this.arcoMapper = arcoMapper;
        this.eventoMensajeMapper = eventoMensajeMapper;
        this.flujoMensajeMapper = flujoMensajeMapper;
    }

    @Override
    public ProcesoDetalleDTO construirDetalle(Proceso proceso) {
        Long procesoId = proceso.getId();

        List<Lane> lanes = laneRepository.findByProcesoIdAndActivoTrueOrderByOrdenAsc(procesoId);

        // La cantidad de lanes de cada pool se calcula con las lanes ya cargadas,
        // sin hacer una consulta adicional por pool.
        Map<Long, Long> lanesPorPool = lanes.stream()
                .map(Lane::getPool)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Pool::getId, Collectors.counting()));

        List<PoolResponseDTO> pools = poolRepository.findByProcesoIdAndActivoTrue(procesoId)
                .stream()
                .map(pool -> poolMapper.toDTO(pool, lanesPorPool.getOrDefault(pool.getId(), 0L)))
                .toList();

        List<LaneDTO> lanesDTO = mapear(lanes, laneMapper::toDTO);

        List<ActividadDTO> actividades = mapear(
                actividadRepository.findByProcesoIdAndActivoTrue(procesoId), actividadMapper::toDTO);

        List<GatewayResponseDTO> gateways = mapear(
                gatewayRepository.findByProcesoIdAndActivoTrue(procesoId), gatewayMapper::toDTO);

        List<ArcoResponseDTO> arcos = mapear(
                arcoRepository.findByProcesoIdAndActivoTrue(procesoId), arcoMapper::toDTO);

        List<EventoMensajeResponseDTO> eventos = mapear(
                eventoMensajeRepository.findByProcesoIdAndActivoTrue(procesoId), eventoMensajeMapper::toDTO);

        List<FlujoMensajeResponseDTO> flujos = mapear(
                flujoMensajeRepository.findByProcesoIdAndActivoTrue(procesoId), flujoMensajeMapper::toDTO);

        return new ProcesoDetalleDTO(
                proceso.getId(),
                proceso.getNombre(),
                proceso.getDescripcion(),
                proceso.getCategoria(),
                proceso.getEstado(),
                proceso.getActivo(),
                pools,
                lanesDTO,
                actividades,
                gateways,
                arcos,
                eventos,
                flujos,
                proceso.getEmpresa().getId()
        );
    }

    private static <E, D> List<D> mapear(List<E> entidades, Function<E, D> mapper) {
        return entidades.stream().map(mapper).toList();
    }
}
