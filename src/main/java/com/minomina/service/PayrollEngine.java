package com.minomina.service;

import com.minomina.model.ConfigNomina;
import com.minomina.model.Registro;
import com.minomina.model.TipoDia;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port directo del motor de cálculo del frontend original (función calcularTurno
 * y calcularAcumuladoSemanal). Reglas:
 *  1. El descanso (break) no remunerado se descuenta minuto a minuto.
 *  2. Umbral diario: 10h laboradas (Lun-Sáb no festivo) u 8h (Dom/Festivo).
 *  3. Contador semanal solo con horas ORDINARIAS físicamente trabajadas
 *     (ordD+ordN días hábiles, domD+domN domingos/festivos hasta su límite).
 *     Las extras, compensatorios e incapacidades NO suman al contador semanal.
 *  4. El ciclo de semanas arranca desde el primer día registrado por el usuario.
 *  5. Cortes legales: 2026-07-01 sube dominical/festivo a 90%; 2026-07-15 pasa
 *     la jornada a 42h/semana y sube el valor hora.
 */
@Service
public class PayrollEngine {

    public static final LocalDate CORTE_DOM90 = LocalDate.of(2026, 7, 1);
    public static final LocalDate CORTE_42H = LocalDate.of(2026, 7, 15);

    private final FestivosService festivosService;

    public PayrollEngine(FestivosService festivosService) {
        this.festivosService = festivosService;
    }

    public boolean esNocturno(LocalDateTime dt) {
        int h = dt.getHour();
        return h >= 19 || h < 6;
    }

    public boolean esDomFest(LocalDate fecha) {
        return fecha.getDayOfWeek().getValue() == 7 || festivosService.esFestivo(fecha); // 7 = domingo
    }

    /** Configuración efectiva del período legal vigente para una fecha dada. */
    public ConfigEfectiva configEfectiva(ConfigNomina base, LocalDate fecha) {
        boolean esDom90 = !fecha.isBefore(CORTE_DOM90);
        boolean es42h = !fecha.isBefore(CORTE_42H);

        double hora = es42h ? 8337 : base.getHora();
        double dom = esDom90 ? 90 : base.getDom();
        double hedd = esDom90 ? 115 : base.getHedd();
        double hend = esDom90 ? 165 : base.getHend();

        return new ConfigEfectiva(hora, base.getRn(), dom, base.getHed(), base.getHen(), hedd, hend, es42h);
    }

    /**
     * Suma de horas ORDINARIAS (ordD+ordN+domD+domN) trabajadas en la semana del
     * ciclo a la que pertenece "fecha", contando solo días NORMAL anteriores a "fecha".
     * "registrosOrdenados" debe venir ya ordenado por fecha ascendente y SIN incluir
     * el registro del día que se está calculando/editando.
     */
    @SuppressWarnings("unchecked")
    public double calcularAcumuladoSemanal(List<Registro> registrosOrdenados, LocalDate fecha) {
        if (registrosOrdenados == null || registrosOrdenados.isEmpty()) return 0;

        LocalDate primerDia = registrosOrdenados.get(0).getFecha();
        long diasDesdeInicio = ChronoUnit.DAYS.between(primerDia, fecha);
        long semanaActual = Math.floorDiv(diasDesdeInicio, 7);

        LocalDate inicioSemana = primerDia.plusDays(semanaActual * 7);
        LocalDate finSemana = inicioSemana.plusDays(7);

        double acumulado = 0;
        for (Registro r : registrosOrdenados) {
            if (!r.getFecha().isBefore(fecha)) continue;           // solo días anteriores
            if (r.getFecha().isBefore(inicioSemana)) continue;     // fuera de la semana
            if (!r.getFecha().isBefore(finSemana)) continue;       // fuera de la semana
            if (r.getTipo() != TipoDia.NORMAL) continue;

            Map<String, Object> d = r.getDesglose();
            acumulado += horasDe(d, "ordD") + horasDe(d, "ordN") + horasDe(d, "domD") + horasDe(d, "domN");
        }
        return acumulado;
    }

    private double horasDe(Map<String, Object> desglose, String key) {
        if (desglose == null) return 0;
        Object entry = desglose.get(key);
        if (!(entry instanceof Map)) return 0;
        Object h = ((Map<?, ?>) entry).get("h");
        if (h == null) return 0;
        return Double.parseDouble(h.toString());
    }

    /**
     * Calcula el desglose de horas y el pago salarial de un turno NORMAL.
     * @param registrosPrevios registros NORMAL/otros del usuario, ordenados asc, SIN el día actual.
     */
    public TurnoResultado calcularTurno(ConfigNomina cfgUsuario, LocalDate fecha,
                                         LocalTime horaInicio, LocalTime horaFin,
                                         LocalTime descansoInicio, LocalTime descansoFin,
                                         List<Registro> registrosPrevios) {

        LocalDateTime start = LocalDateTime.of(fecha, horaInicio);
        LocalDateTime end = LocalDateTime.of(fecha, horaFin);
        if (!end.isAfter(start)) end = end.plusDays(1); // turno cruza medianoche

        LocalDateTime breakStart = null;
        LocalDateTime breakEnd = null;
        if (descansoInicio != null && descansoFin != null) {
            breakStart = LocalDateTime.of(fecha, descansoInicio);
            if (breakStart.isBefore(start)) breakStart = breakStart.plusDays(1);
            breakEnd = LocalDateTime.of(fecha, descansoFin);
            if (!breakEnd.isAfter(breakStart)) breakEnd = breakEnd.plusDays(1);
        }

        boolean esDomFestDia = esDomFest(fecha);
        double acumSemanAnterior = calcularAcumuladoSemanal(registrosPrevios, fecha);

        ConfigEfectiva cfgP = configEfectiva(cfgUsuario, fecha);
        int limiteSemanalMin = (cfgP.es42h() ? 42 : 44) * 60;
        int limiteDiarioMin = esDomFestDia ? 8 * 60 : 10 * 60;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : new String[]{"ordD", "ordN", "extD", "extN", "domD", "domN", "edd", "edn"}) {
            counts.put(k, 0);
        }

        int minsLaborados = 0;
        int minsAcumSemanal = (int) Math.round(acumSemanAnterior * 60);

        LocalDateTime cur = start;
        while (cur.isBefore(end)) {
            boolean inBreak = breakStart != null && !cur.isBefore(breakStart) && cur.isBefore(breakEnd);

            if (!inBreak) {
                minsLaborados++;
                boolean noc = esNocturno(cur);
                boolean fest = esDomFest(cur.toLocalDate());

                boolean extDiaria = minsLaborados > limiteDiarioMin;
                boolean extSemanal = !extDiaria && (minsAcumSemanal > limiteSemanalMin);
                boolean esExtra = extDiaria || extSemanal;

                if (!extDiaria) minsAcumSemanal++;

                String key;
                if (!esExtra && !fest && !noc) key = "ordD";
                else if (!esExtra && !fest) key = "ordN";
                else if (esExtra && !fest && !noc) key = "extD";
                else if (esExtra && !fest) key = "extN";
                else if (!esExtra && !noc) key = "domD";
                else if (!esExtra) key = "domN";
                else if (!noc) key = "edd";
                else key = "edn";

                counts.merge(key, 1, Integer::sum);
            }

            cur = cur.plusMinutes(1);
        }

        Map<String, Double> prices = new LinkedHashMap<>();
        prices.put("ordD", cfgP.hora());
        prices.put("ordN", cfgP.hora() * (1 + cfgP.rn() / 100));
        prices.put("extD", cfgP.hora() * (1 + cfgP.hed() / 100));
        prices.put("extN", cfgP.hora() * (1 + cfgP.hen() / 100));
        prices.put("domD", cfgP.hora() * (1 + cfgP.dom() / 100));
        prices.put("domN", cfgP.hora() * (1 + cfgP.dom() / 100 + cfgP.rn() / 100));
        prices.put("edd", cfgP.hora() * (1 + cfgP.hedd() / 100));
        prices.put("edn", cfgP.hora() * (1 + cfgP.hend() / 100));

        Map<String, Object> desglose = new LinkedHashMap<>();
        double pagoSalarial = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double horas = e.getValue() / 60.0;
            if (horas > 0) {
                double valor = horas * prices.get(e.getKey());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("h", Math.round(horas * 100.0) / 100.0);
                item.put("v", valor);
                desglose.put(e.getKey(), item);
                pagoSalarial += valor;
            }
        }

        return new TurnoResultado(desglose, pagoSalarial);
    }

    /** Config con los porcentajes/valor-hora ya resueltos para el período legal de la fecha. */
    public record ConfigEfectiva(double hora, double rn, double dom, double hed, double hen,
                                  double hedd, double hend, boolean es42h) { }

    public record TurnoResultado(Map<String, Object> desglose, double pagoSalarial) { }
}
