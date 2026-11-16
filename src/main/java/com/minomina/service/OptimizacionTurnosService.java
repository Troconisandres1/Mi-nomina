package com.minomina.service;

import com.minomina.model.ConfigNomina;
import com.minomina.model.Registro;
import com.minomina.model.TipoDia;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Modelo de Programación Lineal Entera Binaria (PLE) resuelto mediante
 * Branch and Bound para la asignación quincenal de turnos.
 */
@Service
public class OptimizacionTurnosService {

    private final PayrollEngine payrollEngine;

    public OptimizacionTurnosService(PayrollEngine payrollEngine) {
        this.payrollEngine = payrollEngine;
    }

    public record CatalogoTurno(
            String codigo,
            String nombre,
            LocalTime horaInicio,
            LocalTime horaFin,
            LocalTime descansoInicio,
            LocalTime descansoFin,
            double horasTotales,
            double horasNocturnas
    ) {}

    public static final List<CatalogoTurno> CATALOGO_TURNOS = List.of(
            new CatalogoTurno("DIURNO_8H", "Diurno Mañana (06:00 - 14:30)",
                    LocalTime.of(6, 0), LocalTime.of(14, 30),
                    LocalTime.of(12, 0), LocalTime.of(12, 30), 8.0, 0.0),
            new CatalogoTurno("CENTRAL_8H", "Diurno Central (08:00 - 16:30)",
                    LocalTime.of(8, 0), LocalTime.of(16, 30),
                    LocalTime.of(12, 30), LocalTime.of(13, 0), 8.0, 0.0),
            new CatalogoTurno("TARDE_8H", "Tarde Estándar (12:00 - 20:30)",
                    LocalTime.of(12, 0), LocalTime.of(20, 30),
                    LocalTime.of(16, 0), LocalTime.of(16, 30), 8.0, 1.5),
            new CatalogoTurno("MIXTO_8H", "Mixto Tarde-Noche (14:00 - 22:30)",
                    LocalTime.of(14, 0), LocalTime.of(22, 30),
                    LocalTime.of(18, 0), LocalTime.of(18, 30), 8.0, 3.5),
            new CatalogoTurno("NOCTURNO_20_8H", "Nocturno Temprano (20:00 - 04:30)",
                    LocalTime.of(20, 0), LocalTime.of(4, 30),
                    LocalTime.of(0, 0), LocalTime.of(0, 30), 8.0, 8.0),
            new CatalogoTurno("NOCTURNO_8H", "Nocturno Completo (22:00 - 06:30)",
                    LocalTime.of(22, 0), LocalTime.of(6, 30),
                    LocalTime.of(2, 0), LocalTime.of(2, 30), 8.0, 7.5),
            new CatalogoTurno("EXTENDIDO_10H", "Diurno Extendido 10h (08:00 - 19:00)",
                    LocalTime.of(8, 0), LocalTime.of(19, 0),
                    LocalTime.of(13, 0), LocalTime.of(14, 0), 10.0, 0.0),
            new CatalogoTurno("TARDE_10H", "Tarde Extendido 10h (12:00 - 23:00)",
                    LocalTime.of(12, 0), LocalTime.of(23, 0),
                    LocalTime.of(17, 0), LocalTime.of(18, 0), 10.0, 4.0),
            new CatalogoTurno("NOCTURNO_10H", "Nocturno Extendido 10h (20:00 - 06:30)",
                    LocalTime.of(20, 0), LocalTime.of(6, 30),
                    LocalTime.of(1, 0), LocalTime.of(1, 30), 10.0, 9.5)
    );

    public enum ModoOptimizacion {
        MAXIMIZAR_DINERO("Maximizar dinero (máximo ingreso neto)"),
        EQUILIBRIO("Equilibrio dinero y descanso (turnos no pesados)"),
        PRIORIZAR_DESCANSO("Priorizar descanso (turnos ligeros y más días libres)");

        private final String descripcion;

        ModoOptimizacion(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    public enum FranjaHoraria {
        TODAS("Todas las franjas (rotativo)"),
        DIURNA("Diurna / Mañana (06:00 - 19:00)"),
        MIXTA("Tarde / Mixta (12:00 - 22:30)"),
        NOCTURNA("Nocturna (20:00 - 06:30)");

        private final String descripcion;

        FranjaHoraria(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

    public record ParametrosOptimizacion(
            LocalDate fechaInicio,
            int diasHorizonte,
            int maxDiasConsecutivos,
            int descansoMinimoHoras,
            int maxDiasLaborablesQuincena,
            Integer diasDescansoQuincena,
            ModoOptimizacion modo,
            FranjaHoraria franjaHoraria,
            String horaInicioTurno
    ) {
        public ParametrosOptimizacion(
                LocalDate fechaInicio,
                int diasHorizonte,
                int maxDiasConsecutivos,
                int descansoMinimoHoras,
                int maxDiasLaborablesQuincena
        ) {
            this(fechaInicio, diasHorizonte, maxDiasConsecutivos, descansoMinimoHoras,
                    maxDiasLaborablesQuincena, null, ModoOptimizacion.MAXIMIZAR_DINERO, FranjaHoraria.TODAS, null);
        }

        public ParametrosOptimizacion(
                LocalDate fechaInicio,
                int diasHorizonte,
                int maxDiasConsecutivos,
                int descansoMinimoHoras,
                int maxDiasLaborablesQuincena,
                ModoOptimizacion modo
        ) {
            this(fechaInicio, diasHorizonte, maxDiasConsecutivos, descansoMinimoHoras,
                    maxDiasLaborablesQuincena, null, modo, FranjaHoraria.TODAS, null);
        }

        public ParametrosOptimizacion(
                LocalDate fechaInicio,
                int diasHorizonte,
                int maxDiasConsecutivos,
                int descansoMinimoHoras,
                int maxDiasLaborablesQuincena,
                Integer diasDescansoQuincena,
                ModoOptimizacion modo,
                FranjaHoraria franjaHoraria
        ) {
            this(fechaInicio, diasHorizonte, maxDiasConsecutivos, descansoMinimoHoras,
                    maxDiasLaborablesQuincena, diasDescansoQuincena, modo, franjaHoraria, null);
        }

        public static ParametrosOptimizacion porDefecto(LocalDate inicio) {
            return new ParametrosOptimizacion(
                    inicio != null ? inicio : LocalDate.of(2026, 11, 1),
                    15,
                    6,
                    12,
                    13,
                    2,
                    ModoOptimizacion.MAXIMIZAR_DINERO,
                    FranjaHoraria.TODAS,
                    null
            );
        }
    }

    public record DiaAsignado(
            int diaIndice,
            LocalDate fecha,
            boolean esDomingoOFestivo,
            boolean descansa,
            String codigoTurno,
            String nombreTurno,
            String horario,
            double horasOrdinarias,
            double horasExtras,
            double horasNocturnas,
            double pagoBruto,
            double auxilioTransporte,
            double deduccionesLey,
            double ingresoNeto
    ) {}

    public record ResultadoOptimizacion(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            ModoOptimizacion modoOptimizacion,
            double limiteSemanalHoras,
            int maxDiasConsecutivos,
            int descansoMinimoHoras,
            double objetivoOptimoNeto,
            double totalBrutoOptimo,
            double totalTransporteOptimo,
            double totalDeduccionesOptimo,
            double totalHorasOptimo,
            double totalHorasNocturnasOptimo,
            double totalHorasFestivasOptimo,
            int diasLaboradosOptimo,
            int diasDescansoOptimo,
            double ingresoNetoAsignacionManual,
            double gananciaNetoCOP,
            double gananciaPorcentaje,
            long nodosExplorados,
            long tiempoEjecucionMs,
            List<DiaAsignado> planOptimo,
            List<DiaAsignado> planManualReferencia,
            Map<String, Object> formulacionMatematica
    ) {}

    private static class EvaluacionCandidato {
        CatalogoTurno turno;
        double horasOrd;
        double horasExt;
        double horasNoc;
        double horasFest;
        double pagoBruto;
        double transporte;
        double deducciones;
        double valorNeto;
        double valorObjetivo;
    }

    public ResultadoOptimizacion optimizarQuincena(ConfigNomina cfg, ParametrosOptimizacion params, List<Registro> registrosActuales) {
        long t0 = System.nanoTime();
        LocalDate fechaInicio = params.fechaInicio() != null ? params.fechaInicio() : LocalDate.of(2026, 11, 1);
        int nDias = Math.max(7, Math.min(16, params.diasHorizonte() > 0 ? params.diasHorizonte() : 15));
        int maxConsec = Math.max(3, Math.min(6, params.maxDiasConsecutivos() > 0 ? params.maxDiasConsecutivos() : 6));
        int minDescansoH = Math.max(8, Math.min(16, params.descansoMinimoHoras() > 0 ? params.descansoMinimoHoras() : 12));
        ModoOptimizacion modo = params.modo() != null ? params.modo() : ModoOptimizacion.MAXIMIZAR_DINERO;
        FranjaHoraria franja = params.franjaHoraria() != null ? params.franjaHoraria() : FranjaHoraria.TODAS;

        int diasDescansoMin;
        if (params.diasDescansoQuincena() != null && params.diasDescansoQuincena() > 0) {
            diasDescansoMin = Math.max(2, Math.min(nDias - 6, params.diasDescansoQuincena()));
        } else if (params.maxDiasLaborablesQuincena() > 0) {
            diasDescansoMin = Math.max(2, nDias - params.maxDiasLaborablesQuincena());
        } else {
            diasDescansoMin = (modo == ModoOptimizacion.PRIORIZAR_DESCANSO) ? 4 : 2;
        }
        if (modo == ModoOptimizacion.PRIORIZAR_DESCANSO && params.diasDescansoQuincena() == null) {
            diasDescansoMin = Math.max(diasDescansoMin, 4);
        }
        int maxDiasQuincena = Math.max(6, Math.min(nDias - 2, nDias - diasDescansoMin));

        PayrollEngine.ConfigEfectiva cfgInicial = payrollEngine.configEfectiva(cfg, fechaInicio);
        double limiteSemanalBase = cfgInicial.es42h() ? 42.0 : 44.0;
        double auxDiario = cfg.getAuxTranspMensual() / 30.0;
        double valorHoraBase = cfg.getHora() > 0 ? cfg.getHora() : 7959.0;
        double bonoDescanso = calcularBonoDescanso(valorHoraBase, modo);

        List<CatalogoTurno> catalogoActivo = construirCatalogoEfectivo(params.horaInicioTurno());
        int numTurnos = catalogoActivo.size();
        boolean tieneHoraFija = params.horaInicioTurno() != null && !params.horaInicioTurno().isBlank();
        boolean[] turnoHabilitado = new boolean[numTurnos];
        for (int s = 0; s < numTurnos; s++) {
            turnoHabilitado[s] = tieneHoraFija || perteneceAFranja(catalogoActivo.get(s), franja);
        }

        EvaluacionCandidato[][] matriz = new EvaluacionCandidato[nDias][numTurnos];
        LocalDate[] fechas = new LocalDate[nDias];
        boolean[] esDomFest = new boolean[nDias];

        for (int d = 0; d < nDias; d++) {
            LocalDate fecha = fechaInicio.plusDays(d);
            fechas[d] = fecha;
            esDomFest[d] = payrollEngine.esDomFest(fecha);

            for (int s = 0; s < numTurnos; s++) {
                CatalogoTurno t = catalogoActivo.get(s);
                PayrollEngine.TurnoResultado res = payrollEngine.calcularTurno(
                        cfg, fecha, t.horaInicio(), t.horaFin(), t.descansoInicio(), t.descansoFin(), Collections.emptyList()
                );
                EvaluacionCandidato ev = new EvaluacionCandidato();
                ev.turno = t;
                ev.horasOrd = extraerHoras(res.desglose(), "ordD") + extraerHoras(res.desglose(), "ordN")
                        + extraerHoras(res.desglose(), "domD") + extraerHoras(res.desglose(), "domN");
                ev.horasExt = extraerHoras(res.desglose(), "extD") + extraerHoras(res.desglose(), "extN")
                        + extraerHoras(res.desglose(), "edd") + extraerHoras(res.desglose(), "edn");
                ev.horasNoc = extraerHoras(res.desglose(), "ordN") + extraerHoras(res.desglose(), "extN")
                        + extraerHoras(res.desglose(), "domN") + extraerHoras(res.desglose(), "edn");
                ev.horasFest = extraerHoras(res.desglose(), "domD") + extraerHoras(res.desglose(), "domN")
                        + extraerHoras(res.desglose(), "edd") + extraerHoras(res.desglose(), "edn");
                ev.pagoBruto = res.pagoSalarial();
                ev.transporte = auxDiario;
                ev.deducciones = ev.pagoBruto * 0.08; // 4% salud + 4% pensión
                ev.valorNeto = ev.pagoBruto + ev.transporte - ev.deducciones;
                ev.valorObjetivo = turnoHabilitado[s]
                        ? calcularValorObjetivo(ev, t, d, s, valorHoraBase, modo, franja, tieneHoraFija)
                        : -1.0;
                matriz[d][s] = ev;
            }
        }

        // Precalcular matriz de compatibilidad de descanso mínimo R4 entre el turno s1 en día d-1 y s2 en día d
        boolean[][] transicionValida = new boolean[numTurnos][numTurnos];
        LocalDate baseDay = LocalDate.of(2026, 1, 1);
        for (int s1 = 0; s1 < numTurnos; s1++) {
            CatalogoTurno t1 = catalogoActivo.get(s1);
            LocalDateTime start1 = LocalDateTime.of(baseDay, t1.horaInicio());
            LocalDateTime end1 = LocalDateTime.of(baseDay, t1.horaFin());
            if (!end1.isAfter(start1)) end1 = end1.plusDays(1);

            for (int s2 = 0; s2 < numTurnos; s2++) {
                CatalogoTurno t2 = catalogoActivo.get(s2);
                LocalDateTime start2 = LocalDateTime.of(baseDay.plusDays(1), t2.horaInicio());
                long minutosDescanso = Duration.between(end1, start2).toMinutes();
                transicionValida[s1][s2] = minutosDescanso >= (minDescansoH * 60L);
            }
        }

        // Cota superior optimista por día para poda en Branch and Bound
        double[] maxPorDia = new double[nDias];
        for (int d = 0; d < nDias; d++) {
            double m = bonoDescanso;
            for (int s = 0; s < numTurnos; s++) {
                if (matriz[d][s].valorObjetivo > m) m = matriz[d][s].valorObjetivo;
            }
            maxPorDia[d] = m;
        }
        double[] cotaRestante = new double[nDias + 1];
        for (int d = nDias - 1; d >= 0; d--) {
            cotaRestante[d] = cotaRestante[d + 1] + maxPorDia[d];
        }

        EstadoBnB estado = new EstadoBnB(nDias);
        int[] asignacionActual = new int[nDias];
        Arrays.fill(asignacionActual, -1);

        resolverBranchAndBound(
                0, nDias, numTurnos, matriz, fechas, transicionValida, cotaRestante,
                maxConsec, maxDiasQuincena, bonoDescanso, cfg,
                0, 0, 0.0, 0.0, 0.0,
                asignacionActual, estado
        );

        List<DiaAsignado> planOptimo = construirPlanDesdeIndices(estado.mejorAsignacion, fechas, esDomFest, matriz);
        List<DiaAsignado> planManual = construirPlanManualReferencia(
                nDias, fechas, esDomFest, matriz, registrosActuales, auxDiario, limiteSemanalBase
        );

        double totalBrutoOpt = 0, totalTranspOpt = 0, totalDedOpt = 0, totalNetoOpt = 0;
        double totalHorasOpt = 0, totalNocOpt = 0, totalFestOpt = 0;
        int diasLabOpt = 0;

        for (DiaAsignado dia : planOptimo) {
            if (!dia.descansa()) {
                diasLabOpt++;
                totalBrutoOpt += dia.pagoBruto();
                totalTranspOpt += dia.auxilioTransporte();
                totalDedOpt += dia.deduccionesLey();
                totalNetoOpt += dia.ingresoNeto();
                totalHorasOpt += dia.horasOrdinarias() + dia.horasExtras();
                totalNocOpt += dia.horasNocturnas();
                if (dia.esDomingoOFestivo()) {
                    totalFestOpt += dia.horasOrdinarias() + dia.horasExtras();
                }
            }
        }

        double netoManual = planManual.stream().mapToDouble(DiaAsignado::ingresoNeto).sum();
        double gananciaCOP = Math.max(0, totalNetoOpt - netoManual);
        double gananciaPct = netoManual > 0 ? (gananciaCOP / netoManual) * 100.0 : 0.0;

        long tiempoMs = Math.max(1L, (System.nanoTime() - t0) / 1_000_000L);

        String funcionObjStr = switch (modo) {
            case MAXIMIZAR_DINERO -> "max Z = sum_{d in D} sum_{s in S} v_{d,s} * x_{d,s}";
            case EQUILIBRIO -> "max Z = sum_{d in D} sum_{s in S} (v_{d,s} - fatiga_s) * x_{d,s} + bono_descanso * dias_libres";
            case PRIORIZAR_DESCANSO -> "max Z = sum_{d in D} sum_{s in S} (v_{d,s} - fatiga_alta_s) * x_{d,s} + bono_descanso_alto * dias_libres";
        };

        Map<String, Object> formulacion = new LinkedHashMap<>();
        formulacion.put("tipoModelo", "Programación Lineal Entera Binaria (PLE) - Método Branch and Bound");
        formulacion.put("modoOptimizacion", modo.getDescripcion());
        formulacion.put("variableDecision", "x_{d,s} in {0, 1}: 1 si el trabajador toma el turno s in S en el día d in D; 0 en caso contrario");
        formulacion.put("funcionObjetivo", funcionObjStr);
        formulacion.put("numVariablesBinarias", nDias * numTurnos);
        formulacion.put("restricciones", List.of(
                "R1 (Asignación única diaria): sum_{s in S} x_{d,s} <= 1  para todo d in D",
                "R2 (Jornada ordinaria semanal Ley 2101/2021): sum_{d in w} sum_{s in S} h^{ord}_{d,s} * x_{d,s} <= " + (int) limiteSemanalBase + "h  para cada semana w",
                "R3 (Límite diario ordinario y tope extra semanal): h^{ord}_{d,s} <= 10h (hábil) / 8h (dom-fest) y sum_{d in w} h^{ext}_{d,s} * x_{d,s} <= 12h",
                "R4 (Descanso inter-jornada y días consecutivos): x_{d-1,s1} + x_{d,s2} <= 1 si descanso(s1,s2) < " + minDescansoH + "h; máximo " + maxConsec + " días continuos sin descanso"
        ));

        return new ResultadoOptimizacion(
                fechaInicio,
                fechaInicio.plusDays(nDias - 1),
                modo,
                limiteSemanalBase,
                maxConsec,
                minDescansoH,
                redondear(totalNetoOpt),
                redondear(totalBrutoOpt),
                redondear(totalTranspOpt),
                redondear(totalDedOpt),
                redondear(totalHorasOpt),
                redondear(totalNocOpt),
                redondear(totalFestOpt),
                diasLabOpt,
                nDias - diasLabOpt,
                redondear(netoManual),
                redondear(gananciaCOP),
                redondear(gananciaPct),
                estado.nodosExplorados,
                tiempoMs,
                planOptimo,
                planManual,
                formulacion
        );
    }

    private List<CatalogoTurno> construirCatalogoEfectivo(String horaInicioTurno) {
        if (horaInicioTurno == null || horaInicioTurno.isBlank()) {
            return CATALOGO_TURNOS;
        }
        try {
            LocalTime inicio = LocalTime.parse(horaInicioTurno.trim());
            LocalTime fin8h = inicio.plusHours(8).plusMinutes(30);
            LocalTime descIni8h = inicio.plusHours(4);
            LocalTime descFin8h = descIni8h.plusMinutes(30);

            LocalTime fin10h = inicio.plusHours(11);
            LocalTime descIni10h = inicio.plusHours(5);
            LocalTime descFin10h = descIni10h.plusHours(1);

            return List.of(
                    new CatalogoTurno("PERSONALIZADO_8H",
                            String.format("Turno 8h (%s - %s)", inicio, fin8h),
                            inicio, fin8h, descIni8h, descFin8h, 8.0, 0.0),
                    new CatalogoTurno("PERSONALIZADO_10H",
                            String.format("Turno Extendido 10h (%s - %s)", inicio, fin10h),
                            inicio, fin10h, descIni10h, descFin10h, 10.0, 0.0)
            );
        } catch (Exception e) {
            return CATALOGO_TURNOS;
        }
    }

    private boolean perteneceAFranja(CatalogoTurno t, FranjaHoraria franja) {
        return switch (franja) {
            case TODAS -> true;
            case DIURNA -> t.codigo().equals("DIURNO_8H") || t.codigo().equals("CENTRAL_8H") || t.codigo().equals("EXTENDIDO_10H");
            case MIXTA -> t.codigo().equals("TARDE_8H") || t.codigo().equals("MIXTO_8H") || t.codigo().equals("TARDE_10H");
            case NOCTURNA -> t.codigo().equals("NOCTURNO_20_8H") || t.codigo().equals("NOCTURNO_8H") || t.codigo().equals("NOCTURNO_10H");
        };
    }

    private double calcularBonoDescanso(double valorHoraBase, ModoOptimizacion modo) {
        return 0.0;
    }

    private double calcularValorObjetivo(
            EvaluacionCandidato ev, CatalogoTurno t, int dia, int sIdx,
            double valorHoraBase, ModoOptimizacion modo, FranjaHoraria franja, boolean tieneHoraFija
    ) {
        if (modo == ModoOptimizacion.MAXIMIZAR_DINERO) {
            double rotacionTieBreak = ((dia + sIdx) % 3) * 15.0;
            return ev.valorNeto + rotacionTieBreak;
        }
        if (t.horasTotales() > 8.0) {
            return -1.0;
        }
        if (tieneHoraFija) {
            return ev.valorNeto;
        }
        if (franja == FranjaHoraria.MIXTA || franja == FranjaHoraria.NOCTURNA) {
            double baseUniforme = valorHoraBase * 8.0;
            double bonoRotacion = ((dia + sIdx) % 2 == 0) ? 500.0 : 0.0;
            return baseUniforme + bonoRotacion;
        }
        double factorNoc = (modo == ModoOptimizacion.EQUILIBRIO) ? 0.55 : 0.95;
        double penalizacionFatiga = ev.horasNoc * valorHoraBase * factorNoc;
        double bonoRotacion = ((dia + sIdx) % 3 == 0) ? (valorHoraBase * 0.65) : (((dia + sIdx) % 2) * 25.0);
        return Math.max(1.0, ev.valorNeto - penalizacionFatiga + bonoRotacion);
    }

    private static class EstadoBnB {
        double mejorValor = -1.0;
        int[] mejorAsignacion;
        long nodosExplorados = 0;

        EstadoBnB(int nDias) {
            this.mejorAsignacion = new int[nDias];
            Arrays.fill(this.mejorAsignacion, -1);
        }
    }

    private void resolverBranchAndBound(
            int dia, int nDias, int numTurnos,
            EvaluacionCandidato[][] matriz,
            LocalDate[] fechas,
            boolean[][] transicionValida,
            double[] cotaRestante,
            int maxConsecutivos,
            int maxDiasQuincena,
            double bonoDescanso,
            ConfigNomina cfg,
            int diasConsecutivosActual,
            int diasTrabajadosTotal,
            double horasOrdSemanaActual,
            double horasExtSemanaActual,
            double valorAcumulado,
            int[] asignacionActual,
            EstadoBnB estado
    ) {
        if (estado.nodosExplorados > 80_000) {
            return;
        }
        estado.nodosExplorados++;

        if (dia == nDias) {
            if (valorAcumulado > estado.mejorValor) {
                estado.mejorValor = valorAcumulado;
                System.arraycopy(asignacionActual, 0, estado.mejorAsignacion, 0, nDias);
            }
            return;
        }

        // Poda por cota superior (Bounding)
        int diasRestantesPermitidos = Math.min(nDias - dia, maxDiasQuincena - diasTrabajadosTotal);
        if (diasRestantesPermitidos <= 0) {
            double valorConDescansosRestantes = valorAcumulado + ((nDias - dia) * bonoDescanso);
            if (valorConDescansosRestantes > estado.mejorValor) {
                estado.mejorValor = valorConDescansosRestantes;
                int[] copia = Arrays.copyOf(asignacionActual, nDias);
                for (int k = dia; k < nDias; k++) copia[k] = -1;
                estado.mejorAsignacion = copia;
            }
            return;
        }
        if (valorAcumulado + cotaRestante[dia] <= estado.mejorValor) {
            return;
        }

        // Reinicio del contador semanal cada 7 días del ciclo quincenal
        double ordSem = (dia % 7 == 0) ? 0.0 : horasOrdSemanaActual;
        double extSem = (dia % 7 == 0) ? 0.0 : horasExtSemanaActual;
        double limiteSemanal = payrollEngine.configEfectiva(cfg, fechas[dia]).es42h() ? 42.0 : 44.0;

        // Ordenar candidatos del día de mayor a menor valor objetivo para acelerar poda
        Integer[] ordenTurnos = new Integer[numTurnos];
        for (int i = 0; i < numTurnos; i++) ordenTurnos[i] = i;
        final int dIdx = dia;
        Arrays.sort(ordenTurnos, (a, b) -> Double.compare(matriz[dIdx][b].valorObjetivo, matriz[dIdx][a].valorObjetivo));

        if (diasConsecutivosActual < maxConsecutivos && diasTrabajadosTotal < maxDiasQuincena) {
            int turnoPrevio = (dia > 0) ? asignacionActual[dia - 1] : -1;
            int evaluados8h = 0;
            int evaluados10h = 0;

            for (int s : ordenTurnos) {
                EvaluacionCandidato ev = matriz[dia][s];
                if (ev.valorObjetivo < 0) {
                    continue;
                }

                if (turnoPrevio != -1 && !transicionValida[turnoPrevio][s]) {
                    continue;
                }

                boolean es10h = ev.turno.horasTotales() > 8.0;
                if (es10h && evaluados10h >= 1) continue;
                if (!es10h && evaluados8h >= 2) continue;

                double cupoOrdDisponible = Math.max(0.0, limiteSemanal - ordSem);
                double ordTomadas = Math.min(ev.horasOrd, cupoOrdDisponible);
                double extPorTopeSemanal = Math.max(0.0, ev.horasOrd - ordTomadas);
                double extTotalesTurno = ev.horasExt + extPorTopeSemanal;

                if (extSem + extTotalesTurno > 12.0 + 1e-6) {
                    continue;
                }

                if (es10h) evaluados10h++;
                else evaluados8h++;

                asignacionActual[dia] = s;
                resolverBranchAndBound(
                        dia + 1, nDias, numTurnos, matriz, fechas, transicionValida, cotaRestante,
                        maxConsecutivos, maxDiasQuincena, bonoDescanso, cfg,
                        diasConsecutivosActual + 1,
                        diasTrabajadosTotal + 1,
                        ordSem + ordTomadas,
                        extSem + extTotalesTurno,
                        valorAcumulado + ev.valorObjetivo,
                        asignacionActual, estado
                );
                asignacionActual[dia] = -1;
            }
        }

        // Ramificar sobre descanso (x_{d,s} = 0 para todo s)
        asignacionActual[dia] = -1;
        resolverBranchAndBound(
                dia + 1, nDias, numTurnos, matriz, fechas, transicionValida, cotaRestante,
                maxConsecutivos, maxDiasQuincena, bonoDescanso, cfg,
                0,
                diasTrabajadosTotal,
                ordSem,
                extSem,
                valorAcumulado + bonoDescanso,
                asignacionActual, estado
        );
    }

    private List<DiaAsignado> construirPlanDesdeIndices(
            int[] asignacion, LocalDate[] fechas, boolean[] esDomFest, EvaluacionCandidato[][] matriz
    ) {
        List<DiaAsignado> plan = new ArrayList<>(fechas.length);
        for (int d = 0; d < fechas.length; d++) {
            int s = asignacion[d];
            if (s < 0) {
                plan.add(new DiaAsignado(
                        d + 1, fechas[d], esDomFest[d], true,
                        "DESCANSO", "Día de descanso obligatorio", "—",
                        0, 0, 0, 0, 0, 0, 0
                ));
            } else {
                EvaluacionCandidato ev = matriz[d][s];
                String horario = ev.turno.horaInicio() + " - " + ev.turno.horaFin();
                plan.add(new DiaAsignado(
                        d + 1, fechas[d], esDomFest[d], false,
                        ev.turno.codigo(), ev.turno.nombre(), horario,
                        redondear(ev.horasOrd), redondear(ev.horasExt), redondear(ev.horasNoc),
                        redondear(ev.pagoBruto), redondear(ev.transporte),
                        redondear(ev.deducciones), redondear(ev.valorNeto)
                ));
            }
        }
        return plan;
    }

    /**
     * Línea base de referencia (turnos diurnos de 8h de lunes a sábado y descanso dominical).
     */
    private List<DiaAsignado> construirPlanManualReferencia(
            int nDias, LocalDate[] fechas, boolean[] esDomFest,
            EvaluacionCandidato[][] matriz, List<Registro> registrosActuales,
            double auxDiario, double limiteSemanal
    ) {
        List<DiaAsignado> manual = new ArrayList<>(nDias);
        double ordSem = 0.0;
        int consec = 0;

        for (int d = 0; d < nDias; d++) {
            if (d % 7 == 0) ordSem = 0.0;
            boolean descansa = esDomFest[d] || consec >= 6 || (ordSem + 8.0 > limiteSemanal);
            if (descansa) {
                consec = 0;
                manual.add(new DiaAsignado(
                        d + 1, fechas[d], esDomFest[d], true,
                        "DESCANSO", "Descanso (Asignación Manual)", "—",
                        0, 0, 0, 0, 0, 0, 0
                ));
            } else {
                consec++;
                EvaluacionCandidato ev = matriz[d][0]; // DIURNO_8H
                ordSem += ev.horasOrd;
                String horario = ev.turno.horaInicio() + " - " + ev.turno.horaFin();
                manual.add(new DiaAsignado(
                        d + 1, fechas[d], esDomFest[d], false,
                        ev.turno.codigo(), ev.turno.nombre(), horario,
                        redondear(ev.horasOrd), redondear(ev.horasExt), redondear(ev.horasNoc),
                        redondear(ev.pagoBruto), redondear(ev.transporte),
                        redondear(ev.deducciones), redondear(ev.valorNeto)
                ));
            }
        }
        return manual;
    }

    private double extraerHoras(Map<String, Object> desglose, String key) {
        if (desglose == null) return 0.0;
        Object item = desglose.get(key);
        if (!(item instanceof Map<?, ?> m)) return 0.0;
        Object h = m.get("h");
        return h != null ? Double.parseDouble(h.toString()) : 0.0;
    }

    private double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
