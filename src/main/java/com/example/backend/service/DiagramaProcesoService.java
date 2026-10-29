package com.example.backend.service;

import com.example.backend.dto.ProcesoDetalleDTO;
import com.example.backend.entity.Proceso;

/**
 * Construye la vista completa del diagrama de un proceso (HU-07):
 * pools, lanes, actividades, gateways, arcos, eventos y flujos de mensaje.
 *
 * Se separó de ProcesoService para que ese servicio solo gestione los datos
 * del proceso y no dependa de los repositorios de cada elemento del diagrama.
 */
public interface DiagramaProcesoService {

    ProcesoDetalleDTO construirDetalle(Proceso proceso);
}
