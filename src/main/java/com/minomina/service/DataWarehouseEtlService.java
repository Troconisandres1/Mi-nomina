package com.minomina.service;

import com.minomina.model.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Servicio ETL y modelo dimensional en estrella (Hecho_Turno y dimensiones).
 */
@Service
public class DataWarehouseEtlService {

    private final FestivosService festivosService;
    private final ResumenService resumenService;

    public DataWarehouseEtlService(FestivosService festivosService, ResumenService resumenService) {
        this.festivosService = festivosService;
        this.resumenService = resumenService;
    }

    public record DimFechaRow(
            int idFecha,
            LocalDate fecha,
            int dia,
            int semanaAnio,
            String quincenaMes,
            int mes,
            String nombreMes,
            int anio,
            String diaSemana,
            boolean esDomingo,
            boolean esFestivo
    ) {}

    public record DimUsuarioRow(
            String idUsuarioDw,
            String codigoAnonimo,
            String rol
    ) {}

    public record DimTipoDiaRow(
            int idTipoDia,
            String codigo,
            String descripcion,
            boolean remunerado
    ) {}

    public record DimTipoHoraRow(
            int idTipoHora,
            String codigo,
            String nombre,
            String categoria,
            double porcentajeRecargo2026
    ) {}

    public record DimQuincenaRow(
            String idQuincena,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            String estado,
            double netoTotal
    ) {}

    public record HechoTurnoRow(
            String idHecho,
            int idFecha,
            String idUsuarioDw,
            int idTipoDia,
            String idQuincena,
            int idTipoHoraPredominante,
            double horasOrdinariasDiurnas,
            double horasOrdinariasNocturnas,
            double horasExtrasDiurnas,
            double horasExtrasNocturnas,
            double horasDominicalesDiurnas,
            double horasDominicalesNocturnas,
            double horasExtrasDomDiurnas,
            double horasExtrasDomNocturnas,
            double horasTotales,
            double pagoSalarialBruto,
            double auxilioTransporte,
            double deduccionesSaludPension,
            double pagoNetoTurno
    ) {}

    public record KpisDashboard(
            double netoActual,
            double netoProyectado15Dias,
            double ingresoPromedioPorDia,
            double totalHorasLaboradas,
            double porcentajeHorasNocturnas,
            double porcentajeHorasFestivas,
            int diasRegistradosQuincena,
            double cumplimientoMeta15DiasPct,
            double promedioQuincenasAnteriores,
            double variacionEntreQuincenasPct
    ) {}

    public record ModeloEstrellaPayload(
            String fechaExtraccionEtl,
            String granoModelo,
            KpisDashboard kpis,
            List<DimFechaRow> dimFecha,
            List<DimUsuarioRow> dimUsuario,
            List<DimTipoDiaRow> dimTipoDia,
            List<DimTipoHoraRow> dimTipoHora,
            List<DimQuincenaRow> dimQuincena,
            List<HechoTurnoRow> hechoTurno
    ) {}

    public ModeloEstrellaPayload ejecutarEtlUsuario(Usuario usuario, List<Registro> registrosActuales, List<Historial> historialCerrado) {
        ConfigNomina cfg = usuario.getConfig() != null ? usuario.getConfig() : ConfigNomina.porDefecto();
        double auxDiario = cfg.getAuxTranspMensual() / 30.0;

        String idUsuarioAnon = anonimizarId(usuario.getId());
        List<DimUsuarioRow> dimUsuario = List.of(
                new DimUsuarioRow(idUsuarioAnon, idUsuarioAnon, usuario.getRol().name())
        );

        List<DimTipoDiaRow> dimTipoDia = List.of(
                new DimTipoDiaRow(1, "NORMAL", "Turno laborado", true),
                new DimTipoDiaRow(2, "INCAPACIDAD", "Incapacidad médica", true),
                new DimTipoDiaRow(3, "COMPENSATORIO", "Día compensatorio", true),
                new DimTipoDiaRow(4, "NO_LABORADO", "No citado / Descanso", false)
        );

        List<DimTipoHoraRow> dimTipoHora = List.of(
                new DimTipoHoraRow(1, "ordD", "Ordinaria Diurna", "Ordinaria", 0.0),
                new DimTipoHoraRow(2, "ordN", "Ordinaria Nocturna", "Nocturna", 35.0),
                new DimTipoHoraRow(3, "extD", "Extra Diurna", "Extra", 25.0),
                new DimTipoHoraRow(4, "extN", "Extra Nocturna", "Extra / Nocturna", 75.0),
                new DimTipoHoraRow(5, "domD", "Dominical o Festiva Diurna", "Dominical/Festiva", 90.0),
                new DimTipoHoraRow(6, "domN", "Dominical o Festiva Nocturna", "Dominical/Festiva + Nocturna", 125.0),
                new DimTipoHoraRow(7, "edd", "Extra Dominical/Festiva Diurna", "Extra Dominical", 115.0),
                new DimTipoHoraRow(8, "edn", "Extra Dominical/Festiva Nocturna", "Extra Dominical Nocturna", 165.0)
        );

        Map<Integer, DimFechaRow> mapaFechas = new LinkedHashMap<>();
        List<DimQuincenaRow> dimQuincena = new ArrayList<>();
        List<HechoTurnoRow> hechoTurno = new ArrayList<>();

        // 1. Quincena abierta actual
        ResumenService.Resumen resActual = resumenService.calcular(registrosActuales, cfg);
        LocalDate desdeActual = registrosActuales.isEmpty() ? LocalDate.now().withDayOfMonth(1) : registrosActuales.get(0).getFecha();
        LocalDate hastaActual = registrosActuales.isEmpty() ? LocalDate.now() : registrosActuales.get(registrosActuales.size() - 1).getFecha();
        String idQActual = "Q-ABIERTA";

        dimQuincena.add(new DimQuincenaRow(
                idQActual,
                desdeActual + " a " + hastaActual,
                desdeActual,
                hastaActual,
                "ABIERTA",
                redondear(resActual.neto())
        ));

        int seqHecho = 1;
        for (Registro r : registrosActuales) {
            DimFechaRow df = construirDimFecha(r.getFecha());
            mapaFechas.put(df.idFecha(), df);

            HechoTurnoRow hecho = construirHechoDesdeMapa(
                    "HT-" + (seqHecho++),
                    df.idFecha(),
                    idUsuarioAnon,
                    r.getTipo() != null ? r.getTipo().name() : "NORMAL",
                    idQActual,
                    r.getPagoSalarial(),
                    r.isPagaTransporte() ? auxDiario : 0.0,
                    r.getDesglose()
            );
            hechoTurno.add(hecho);
        }

        // 2. Quincenas cerradas en el historial
        int idxHist = 1;
        for (Historial h : historialCerrado) {
            String idQ = "Q-HIST-" + (idxHist++);
            dimQuincena.add(new DimQuincenaRow(
                    idQ,
                    h.getDesde() + " a " + h.getHasta(),
                    h.getDesde(),
                    h.getHasta(),
                    "CERRADA",
                    redondear(h.getNeto())
            ));

            Object regsRaw = h.getSnapshot() != null ? h.getSnapshot().get("registros") : null;
            if (regsRaw instanceof List<?> lista) {
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> m) {
                        Object fechaObj = m.get("fecha");
                        if (fechaObj == null) continue;
                        LocalDate f = LocalDate.parse(fechaObj.toString());
                        DimFechaRow df = construirDimFecha(f);
                        mapaFechas.put(df.idFecha(), df);

                        String tipoStr = m.get("tipo") != null ? m.get("tipo").toString() : "NORMAL";
                        double pagoSal = m.get("pagoSalarial") != null ? Double.parseDouble(m.get("pagoSalarial").toString()) : 0.0;
                        boolean pagaTrans = m.get("pagaTransporte") != null && Boolean.parseBoolean(m.get("pagaTransporte").toString());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> desg = (m.get("desglose") instanceof Map<?, ?>) ? (Map<String, Object>) m.get("desglose") : Collections.emptyMap();

                        HechoTurnoRow hecho = construirHechoDesdeMapa(
                                "HT-" + (seqHecho++),
                                df.idFecha(),
                                idUsuarioAnon,
                                tipoStr,
                                idQ,
                                pagoSal,
                                pagaTrans ? auxDiario : 0.0,
                                desg
                        );
                        hechoTurno.add(hecho);
                    }
                }
            }
        }

        // 3. Cálculo de KPIs analíticos
        int diasReg = registrosActuales.size();
        double netoActual = resActual.neto();
        double ingresoPromDia = diasReg > 0 ? netoActual / diasReg : 0.0;
        double netoProyectado15 = diasReg > 0 ? ingresoPromDia * 15.0 : 0.0;
        double cumplimiento15Pct = Math.min(100.0, (diasReg / 15.0) * 100.0);

        double totalHoras = 0.0, horasNoc = 0.0, horasFest = 0.0;
        for (HechoTurnoRow ht : hechoTurno) {
            if (!"Q-ABIERTA".equals(ht.idQuincena())) continue;
            totalHoras += ht.horasTotales();
            horasNoc += ht.horasOrdinariasNocturnas() + ht.horasExtrasNocturnas()
                    + ht.horasDominicalesNocturnas() + ht.horasExtrasDomNocturnas();
            horasFest += ht.horasDominicalesDiurnas() + ht.horasDominicalesNocturnas()
                    + ht.horasExtrasDomDiurnas() + ht.horasExtrasDomNocturnas();
        }

        double pctNocturnas = totalHoras > 0 ? (horasNoc / totalHoras) * 100.0 : 0.0;
        double pctFestivas = totalHoras > 0 ? (horasFest / totalHoras) * 100.0 : 0.0;

        double promHist = historialCerrado.isEmpty()
                ? 0.0
                : historialCerrado.stream().mapToDouble(Historial::getNeto).average().orElse(0.0);
        double variacionPct = promHist > 0 ? ((netoProyectado15 - promHist) / promHist) * 100.0 : 0.0;

        KpisDashboard kpis = new KpisDashboard(
                redondear(netoActual),
                redondear(netoProyectado15),
                redondear(ingresoPromDia),
                redondear(totalHoras),
                redondear(pctNocturnas),
                redondear(pctFestivas),
                diasReg,
                redondear(cumplimiento15Pct),
                redondear(promHist),
                redondear(variacionPct)
        );

        return new ModeloEstrellaPayload(
                LocalDate.now().toString(),
                "Un turno liquidado por día y usuario (Esquema en Estrella - Kimball)",
                kpis,
                new ArrayList<>(mapaFechas.values()),
                dimUsuario,
                dimTipoDia,
                dimTipoHora,
                dimQuincena,
                hechoTurno
        );
    }

    private DimFechaRow construirDimFecha(LocalDate f) {
        int idFecha = f.getYear() * 10000 + f.getMonthValue() * 100 + f.getDayOfMonth();
        int semana = f.get(WeekFields.ISO.weekOfWeekBasedYear());
        String quincenaMes = f.getDayOfMonth() <= 15 ? "Q1 (1-15)" : "Q2 (16-Fin)";
        String nombreMes = f.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CO"));
        String diaSemana = f.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CO"));
        boolean esDom = f.getDayOfWeek().getValue() == 7;
        boolean esFest = festivosService.esFestivo(f);
        return new DimFechaRow(idFecha, f, f.getDayOfMonth(), semana, quincenaMes, f.getMonthValue(),
                nombreMes, f.getYear(), diaSemana, esDom, esFest);
    }

    private HechoTurnoRow construirHechoDesdeMapa(
            String idHecho, int idFecha, String idUsuarioDw, String tipoDiaStr, String idQuincena,
            double pagoSalarial, double auxTransporte, Map<String, Object> desglose
    ) {
        int idTipoDia = switch (tipoDiaStr) {
            case "INCAPACIDAD" -> 2;
            case "COMPENSATORIO" -> 3;
            case "NO_LABORADO" -> 4;
            default -> 1;
        };

        double ordD = horasDe(desglose, "ordD");
        double ordN = horasDe(desglose, "ordN");
        double extD = horasDe(desglose, "extD");
        double extN = horasDe(desglose, "extN");
        double domD = horasDe(desglose, "domD");
        double domN = horasDe(desglose, "domN");
        double edd = horasDe(desglose, "edd");
        double edn = horasDe(desglose, "edn");

        double totalH = ordD + ordN + extD + extN + domD + domN + edd + edn;
        double deducciones = "INCAPACIDAD".equals(tipoDiaStr) ? 0.0 : pagoSalarial * 0.08;
        double neto = pagoSalarial + auxTransporte - deducciones;

        int idHoraPred = determinarTipoHoraPredominante(ordD, ordN, extD, extN, domD, domN, edd, edn);

        return new HechoTurnoRow(
                idHecho, idFecha, idUsuarioDw, idTipoDia, idQuincena, idHoraPred,
                redondear(ordD), redondear(ordN), redondear(extD), redondear(extN),
                redondear(domD), redondear(domN), redondear(edd), redondear(edn),
                redondear(totalH), redondear(pagoSalarial), redondear(auxTransporte),
                redondear(deducciones), redondear(neto)
        );
    }

    private int determinarTipoHoraPredominante(double... hs) {
        int bestIdx = 1;
        double max = -1;
        for (int i = 0; i < hs.length; i++) {
            if (hs[i] > max) {
                max = hs[i];
                bestIdx = i + 1;
            }
        }
        return bestIdx;
    }

    private double horasDe(Map<String, Object> desglose, String key) {
        if (desglose == null) return 0.0;
        Object item = desglose.get(key);
        if (!(item instanceof Map<?, ?> m)) return 0.0;
        Object h = m.get("h");
        return h != null ? Double.parseDouble(h.toString()) : 0.0;
    }

    public String anonimizarId(String rawId) {
        if (rawId == null || rawId.isBlank()) return "USR-ANON00";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("USR-");
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "USR-ANON01";
        }
    }

    private double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
