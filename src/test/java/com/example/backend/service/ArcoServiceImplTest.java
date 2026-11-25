package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.example.backend.dto.ArcoRequestDTO;
import com.example.backend.dto.EliminacionArcoResponseDTO;
import com.example.backend.entity.Actividad;
import com.example.backend.entity.Arco;
import com.example.backend.entity.Gateway;
import com.example.backend.entity.Lane;
import com.example.backend.entity.Pool;
import com.example.backend.entity.Proceso;
import com.example.backend.entity.RolUsuario;
import com.example.backend.entity.TipoGateway;
import com.example.backend.entity.TipoNodo;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.PermisoDenegadoException;
import com.example.backend.exception.RecursoDuplicadoException;
import com.example.backend.mapper.ArcoMapper;
import com.example.backend.repository.ActividadRepository;
import com.example.backend.repository.ArcoRepository;
import com.example.backend.repository.EventoMensajeRepository;
import com.example.backend.repository.GatewayRepository;
import com.example.backend.repository.ProcesoRepository;
import com.example.backend.sesion.UsuarioSesion;

/**
 * Pruebas unitarias de las reglas de los arcos (HU-11, HU-13, HU-14 y HU-15).
 * Los repositorios, el historial y el mapper se simulan con Mockito; el
 * ValidadorAcceso es real y usa un repositorio de procesos simulado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArcoServiceImplTest {

    private static final long PROCESO = 10L;
    private static final long EMPRESA = 1L;

    @Mock private ArcoRepository arcoRepository;
    @Mock private ActividadRepository actividadRepository;
    @Mock private GatewayRepository gatewayRepository;
    @Mock private EventoMensajeRepository eventoMensajeRepository;
    @Mock private HistorialService historialService;
    @Mock private ProcesoRepository procesoRepository;
    @Mock private ArcoMapper arcoMapper;

    private ArcoServiceImpl servicio;
    private final UsuarioSesion admin = new UsuarioSesion(7L, "Ana", EMPRESA, RolUsuario.ADMINISTRADOR);
    private final Proceso proceso = new Proceso();
    private final Pool poolA = pool(100L);
    private final Pool poolB = pool(200L);

    @BeforeEach
    void preparar() {
        servicio = new ArcoServiceImpl(arcoRepository, actividadRepository, gatewayRepository,
                eventoMensajeRepository, historialService, new ValidadorAcceso(procesoRepository), arcoMapper);
        when(procesoRepository.findByIdAndEmpresaId(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> {
            Arco a = inv.getArgument(0);
            a.setId(55L);
            return a;
        });
        actividad(1L, poolA);
        actividad(2L, poolA);
        actividad(3L, poolB);
    }

    @Test
    @DisplayName("Crea un arco entre dos actividades del mismo pool y registra el historial")
    void creaArcoValido() {
        servicio.crearArco(PROCESO, arco(TipoNodo.ACTIVIDAD, 1L, TipoNodo.ACTIVIDAD, 2L, null), admin);

        ArgumentCaptor<Arco> guardado = ArgumentCaptor.forClass(Arco.class);
        verify(arcoRepository).save(guardado.capture());
        assertEquals(2L, guardado.getValue().getDestinoId());
        assertTrue(guardado.getValue().getActivo());
        verify(historialService).registrar(eq(proceso), eq(admin), anyString());
    }

    @Test
    @DisplayName("Un usuario de solo lectura no puede crear arcos")
    void lecturaNoCrea() {
        UsuarioSesion lector = new UsuarioSesion(8L, "Luis", EMPRESA, RolUsuario.LECTURA);
        ArcoRequestDTO request = arco(TipoNodo.ACTIVIDAD, 1L, TipoNodo.ACTIVIDAD, 2L, null);
        assertThrows(PermisoDenegadoException.class, () -> servicio.crearArco(PROCESO, request, lector));
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("El origen y el destino no pueden ser el mismo nodo")
    void mismoNodo() {
        ArcoRequestDTO request = arco(TipoNodo.ACTIVIDAD, 1L, TipoNodo.ACTIVIDAD, 1L, null);
        assertThrows(OperacionInvalidaException.class, () -> servicio.crearArco(PROCESO, request, admin));
    }

    @Test
    @DisplayName("Los IDs de los nodos deben ser positivos y obligatorios")
    void datosIncompletos() {
        ArcoRequestDTO negativo = arco(TipoNodo.ACTIVIDAD, -1L, TipoNodo.ACTIVIDAD, 2L, null);
        ArcoRequestDTO sinDestino = arco(TipoNodo.ACTIVIDAD, 1L, null, 2L, null);
        assertThrows(OperacionInvalidaException.class, () -> servicio.crearArco(PROCESO, negativo, admin));
        assertThrows(OperacionInvalidaException.class, () -> servicio.crearArco(PROCESO, sinDestino, admin));
    }

    @Test
    @DisplayName("Un arco no puede unir nodos de pools distintos")
    void poolsDistintos() {
        ArcoRequestDTO request = arco(TipoNodo.ACTIVIDAD, 1L, TipoNodo.ACTIVIDAD, 3L, null);
        OperacionInvalidaException e = assertThrows(OperacionInvalidaException.class,
                () -> servicio.crearArco(PROCESO, request, admin));
        assertTrue(e.getMessage().contains("flujo de mensajes"));
    }

    @Test
    @DisplayName("No se permite un arco repetido entre los mismos nodos")
    void arcoRepetido() {
        when(arcoRepository.existsByProcesoIdAndTipoOrigenAndOrigenIdAndTipoDestinoAndDestinoIdAndActivoTrue(
                PROCESO, TipoNodo.ACTIVIDAD, 1L, TipoNodo.ACTIVIDAD, 2L)).thenReturn(true);
        ArcoRequestDTO request = arco(TipoNodo.ACTIVIDAD, 1L, TipoNodo.ACTIVIDAD, 2L, null);
        assertThrows(RecursoDuplicadoException.class, () -> servicio.crearArco(PROCESO, request, admin));
    }

    @Test
    @DisplayName("Un arco que sale de un gateway exclusivo exige condición")
    void exclusivoSinCondicion() {
        gateway(50L, TipoGateway.EXCLUSIVO);
        ArcoRequestDTO request = arco(TipoNodo.GATEWAY, 50L, TipoNodo.ACTIVIDAD, 1L, "  ");
        OperacionInvalidaException e = assertThrows(OperacionInvalidaException.class,
                () -> servicio.crearArco(PROCESO, request, admin));
        assertTrue(e.getMessage().contains("condición"));
    }

    @Test
    @DisplayName("La condición de un gateway exclusivo se guarda sin espacios sobrantes")
    void exclusivoConCondicion() {
        gateway(50L, TipoGateway.EXCLUSIVO);
        servicio.crearArco(PROCESO, arco(TipoNodo.GATEWAY, 50L, TipoNodo.ACTIVIDAD, 1L, "  monto > 1000  "), admin);

        ArgumentCaptor<Arco> guardado = ArgumentCaptor.forClass(Arco.class);
        verify(arcoRepository).save(guardado.capture());
        assertEquals("monto > 1000", guardado.getValue().getCondicion());
    }

    @Test
    @DisplayName("Un arco de un gateway paralelo no admite condición")
    void paraleloConCondicion() {
        gateway(60L, TipoGateway.PARALELO);
        ArcoRequestDTO conCondicion = arco(TipoNodo.GATEWAY, 60L, TipoNodo.ACTIVIDAD, 1L, "x > 1");
        assertThrows(OperacionInvalidaException.class, () -> servicio.crearArco(PROCESO, conCondicion, admin));

        servicio.crearArco(PROCESO, arco(TipoNodo.GATEWAY, 60L, TipoNodo.ACTIVIDAD, 1L, null), admin);
        ArgumentCaptor<Arco> guardado = ArgumentCaptor.forClass(Arco.class);
        verify(arcoRepository).save(guardado.capture());
        assertNull(guardado.getValue().getCondicion());
    }

    @Test
    @DisplayName("Al eliminar el único arco de un nodo se advierte que queda desconectado")
    void eliminarConAdvertencias() {
        Arco existente = new Arco();
        existente.setId(55L);
        existente.setTipoOrigen(TipoNodo.ACTIVIDAD);
        existente.setOrigenId(1L);
        existente.setTipoDestino(TipoNodo.ACTIVIDAD);
        existente.setDestinoId(2L);
        when(arcoRepository.findByIdAndProcesoIdAndActivoTrue(55L, PROCESO)).thenReturn(Optional.of(existente));
        when(arcoRepository.countByProcesoIdAndTipoOrigenAndOrigenIdAndActivoTrue(PROCESO, TipoNodo.ACTIVIDAD, 1L))
                .thenReturn(1L);
        when(arcoRepository.countByProcesoIdAndTipoDestinoAndDestinoIdAndActivoTrue(PROCESO, TipoNodo.ACTIVIDAD, 2L))
                .thenReturn(3L);

        EliminacionArcoResponseDTO respuesta = servicio.eliminarArco(PROCESO, 55L, admin);

        assertEquals(1, respuesta.getAdvertencias().size());
        assertTrue(respuesta.getAdvertencias().get(0).contains("sin camino de salida"));
        assertEquals(Boolean.FALSE, existente.getActivo());
    }

    @Test
    @DisplayName("Solo el administrador elimina arcos")
    void editorNoElimina() {
        UsuarioSesion editor = new UsuarioSesion(9L, "Eva", EMPRESA, RolUsuario.EDITOR);
        assertThrows(PermisoDenegadoException.class, () -> servicio.eliminarArco(PROCESO, 55L, editor));
    }

    // ------------------------------------------------------------------ datos de apoyo

    private static Pool pool(long id) {
        Pool p = new Pool();
        p.setId(id);
        return p;
    }

    private void actividad(long id, Pool pool) {
        Lane lane = new Lane();
        lane.setPool(pool);
        Actividad a = new Actividad();
        a.setId(id);
        a.setLane(lane);
        when(actividadRepository.findByIdAndProcesoIdAndActivoTrue(id, PROCESO)).thenReturn(Optional.of(a));
    }

    private void gateway(long id, TipoGateway tipo) {
        Gateway g = new Gateway();
        g.setId(id);
        g.setTipo(tipo);
        g.setPool(poolA);
        when(gatewayRepository.findByIdAndProcesoIdAndActivoTrue(id, PROCESO)).thenReturn(Optional.of(g));
    }

    private static ArcoRequestDTO arco(TipoNodo tipoOrigen, Long origen, TipoNodo tipoDestino, Long destino,
            String condicion) {
        ArcoRequestDTO r = new ArcoRequestDTO();
        r.setTipoOrigen(tipoOrigen);
        r.setOrigenId(origen);
        r.setTipoDestino(tipoDestino);
        r.setDestinoId(destino);
        r.setCondicion(condicion);
        return r;
    }
}
