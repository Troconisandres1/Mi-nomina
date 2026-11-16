package com.minomina.service;

import com.minomina.model.*;
import com.minomina.service.DataWarehouseEtlService.ModeloEstrellaPayload;
import com.minomina.service.OptimizacionTurnosService.ParametrosOptimizacion;
import com.minomina.service.OptimizacionTurnosService.ResultadoOptimizacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimizacionYDwTest {

    private final FestivosService festivosService = new FestivosService();
    private final PayrollEngine payrollEngine = new PayrollEngine(festivosService);
    private final ResumenService resumenService = new ResumenService();
    private final OptimizacionTurnosService optimizacionService = new OptimizacionTurnosService(payrollEngine);
    private final DataWarehouseEtlService dwEtlService = new DataWarehouseEtlService(festivosService, resumenService);

    @Test
    void modeloPleOptimizaQuincenaCumpliendoRestriccionesLegales() {
        ConfigNomina cfg = ConfigNomina.porDefecto();
        ParametrosOptimizacion params = new ParametrosOptimizacion(
                LocalDate.of(2026, 11, 1),
                15,
                6,
                12,
                13
        );

        ResultadoOptimizacion res = optimizacionService.optimizarQuincena(cfg, params, Collections.emptyList());

        assertNotNull(res);
        assertEquals(15, res.planOptimo().size());
        assertEquals(42.0, res.limiteSemanalHoras());
        assertTrue(res.objetivoOptimoNeto() > res.ingresoNetoAsignacionManual(),
                "La solución óptima PLE debe superar el ingreso neto de la asignación manual tradicional");
        assertTrue(res.gananciaNetoCOP() > 0);
        assertTrue(res.diasLaboradosOptimo() <= 13);

        // Verificar R4: máximo de días consecutivos sin descanso <= 6
        int consecutivos = 0;
        for (var dia : res.planOptimo()) {
            if (!dia.descansa()) {
                consecutivos++;
                assertTrue(consecutivos <= 6, "No debe superar 6 días consecutivos laborados");
            } else {
                consecutivos = 0;
            }
        }
    }

    @Test
    void modoEquilibrioYDescansoEvitanTurnosPesados() {
        ConfigNomina cfg = ConfigNomina.porDefecto();
        LocalDate inicio = LocalDate.of(2026, 11, 1);

        var paramsMax = new ParametrosOptimizacion(
                inicio, 15, 6, 12, 13, OptimizacionTurnosService.ModoOptimizacion.MAXIMIZAR_DINERO);
        var paramsEquilibrio = new ParametrosOptimizacion(
                inicio, 15, 6, 12, 13, OptimizacionTurnosService.ModoOptimizacion.EQUILIBRIO);
        var paramsDescanso = new ParametrosOptimizacion(
                inicio, 15, 6, 12, 13, OptimizacionTurnosService.ModoOptimizacion.PRIORIZAR_DESCANSO);

        ResultadoOptimizacion resMax = optimizacionService.optimizarQuincena(cfg, paramsMax, Collections.emptyList());
        ResultadoOptimizacion resEq = optimizacionService.optimizarQuincena(cfg, paramsEquilibrio, Collections.emptyList());
        ResultadoOptimizacion resDesc = optimizacionService.optimizarQuincena(cfg, paramsDescanso, Collections.emptyList());

        assertEquals(OptimizacionTurnosService.ModoOptimizacion.EQUILIBRIO, resEq.modoOptimizacion());
        assertEquals(OptimizacionTurnosService.ModoOptimizacion.PRIORIZAR_DESCANSO, resDesc.modoOptimizacion());

        assertTrue(resEq.totalHorasNocturnasOptimo() < resMax.totalHorasNocturnasOptimo(),
                "Equilibrio debe asignar menos horas nocturnas que Maximizar Dinero");
        boolean tieneTurno10hEnEquilibrio = resEq.planOptimo().stream()
                .anyMatch(d -> !d.descansa() && d.codigoTurno().endsWith("_10H"));
        assertFalse(tieneTurno10hEnEquilibrio,
                "Equilibrio no debe asignar turnos pesados de 10 horas");

        assertTrue(resDesc.diasDescansoOptimo() >= resMax.diasDescansoOptimo(),
                "Priorizar descanso debe mantener o aumentar los días libres");
    }

    @Test
    void etlDataWarehouseGeneraEsquemaEstrellaConAnonimizacion() {
        Usuario u = new Usuario("trabajador@minomina.co", "hash", "Andrés Troconis");
        u.setId("uuid-1234-5678");
        u.setRol(RolUsuario.USER);

        Registro r1 = new Registro();
        r1.setId("reg-1");
        r1.setUsuario(u);
        r1.setFecha(LocalDate.of(2026, 11, 2)); // Festivo en Colombia
        r1.setTipo(TipoDia.NORMAL);
        r1.setHoraInicio(LocalTime.of(22, 0));
        r1.setHoraFin(LocalTime.of(6, 0));
        var calc = payrollEngine.calcularTurno(u.getConfig(), r1.getFecha(), r1.getHoraInicio(), r1.getHoraFin(), null, null, Collections.emptyList());
        r1.setDesglose(calc.desglose());
        r1.setPagoSalarial(calc.pagoSalarial());
        r1.setPagaTransporte(true);

        ModeloEstrellaPayload dw = dwEtlService.ejecutarEtlUsuario(u, List.of(r1), Collections.emptyList());

        assertNotNull(dw);
        assertEquals(1, dw.dimUsuario().size());
        assertTrue(dw.dimUsuario().get(0).idUsuarioDw().startsWith("USR-"));
        assertNotEquals("uuid-1234-5678", dw.dimUsuario().get(0).idUsuarioDw());
        assertEquals(4, dw.dimTipoDia().size());
        assertEquals(8, dw.dimTipoHora().size());
        assertEquals(1, dw.hechoTurno().size());
        assertTrue(dw.kpis().netoActual() > 0);
        assertTrue(dw.kpis().netoProyectado15Dias() > dw.kpis().netoActual());
    }
}
