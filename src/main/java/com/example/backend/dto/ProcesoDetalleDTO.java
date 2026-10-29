package com.example.backend.dto;

import com.example.backend.entity.EstadoProceso;

import java.util.List;

public class ProcesoDetalleDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private String categoria;
    private EstadoProceso estado;
    private Boolean activo;
    private List<PoolResponseDTO> pools;
    private List<LaneDTO> lanes;
    private List<ActividadDTO> actividades;
    private List<GatewayResponseDTO> gateways;
    private List<ArcoResponseDTO> arcos;

    private List<EventoMensajeResponseDTO> eventosMensaje;
    private List<FlujoMensajeResponseDTO> flujosMensaje;
    // Empresa propietaria: permite saber si el proceso es propio o compartido (HU-23)
    private Long empresaId;

    public ProcesoDetalleDTO(
            Long id,
            String nombre,
            String descripcion,
            String categoria,
            EstadoProceso estado,
            Boolean activo,
            List<PoolResponseDTO> pools,
            List<LaneDTO> lanes,
            List<ActividadDTO> actividades,
            List<GatewayResponseDTO> gateways,
            List<ArcoResponseDTO> arcos,
            List<EventoMensajeResponseDTO> eventosMensaje,
            List<FlujoMensajeResponseDTO> flujosMensaje,
            Long empresaId) {

        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.estado = estado;
        this.activo = activo;
        this.pools = pools;
        this.lanes = lanes;
        this.actividades = actividades;
        this.gateways = gateways;
        this.arcos = arcos;
        this.eventosMensaje = eventosMensaje;
        this.flujosMensaje = flujosMensaje;
        this.empresaId = empresaId;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCategoria() {
        return categoria;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public Boolean getActivo() {
        return activo;
    }

    public List<PoolResponseDTO> getPools() {
        return pools;
    }

    public List<LaneDTO> getLanes() {
        return lanes;
    }

    public List<ActividadDTO> getActividades() {
        return actividades;
    }

    public List<GatewayResponseDTO> getGateways() {
        return gateways;
    }

    public List<ArcoResponseDTO> getArcos() {
        return arcos;
    }
    public List<EventoMensajeResponseDTO> getEventosMensaje() {
        return eventosMensaje;
    }
    public List<FlujoMensajeResponseDTO> getFlujosMensaje() {
        return flujosMensaje;
    }
    public Long getEmpresaId() {
        return empresaId;
    }
}
