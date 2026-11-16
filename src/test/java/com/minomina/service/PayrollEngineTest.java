package com.minomina.service;

import com.minomina.model.ConfigNomina;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayrollEngineTest {

    // Lunes hábil previo al corte de julio de 2026 (tarifa base $7.959/h, jornada 44h/sem)
    private static final LocalDate LUNES_NO_FESTIVO = LocalDate.of(2026, 1, 5);

    private final PayrollEngine engine = new PayrollEngine(new FestivosService());

    @Test
    void confirmaQueLaFechaDePruebaEsUnLunes() {
        assertEquals(DayOfWeek.MONDAY, LUNES_NO_FESTIVO.getDayOfWeek());
    }

    @Test
    void turnoOrdinarioDe8HorasSePagaCompletoComoHoraOrdinariaDiurna() {
        ConfigNomina cfg = ConfigNomina.porDefecto();

        PayrollEngine.TurnoResultado resultado = engine.calcularTurno(
                cfg,
                LUNES_NO_FESTIVO,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0), // 8 horas exactas, sin descanso
                null, null,
                List.of() // sin registros previos en la semana
        );

        double pagoEsperado = 8 * cfg.getHora(); // 8h * $7.959 = $63.672
        assertEquals(pagoEsperado, resultado.pagoSalarial(), 0.01,
                "El pago de 8 horas ordinarias debe ser 8 × valor hora");

        assertTrue(resultado.desglose().containsKey("ordD"),
                "Un turno diurno de lunes a sábado sin exceder límites debe clasificarse como 'ordD'");

        @SuppressWarnings("unchecked")
        Map<String, Object> ordD = (Map<String, Object>) resultado.desglose().get("ordD");
        assertEquals(8.0, (Double) ordD.get("h"), 0.01, "Deben contabilizarse las 8 horas completas");

        // No debe haber ninguna hora clasificada como extra, nocturna o dominical.
        assertFalse(resultado.desglose().containsKey("extD"));
        assertFalse(resultado.desglose().containsKey("extN"));
        assertFalse(resultado.desglose().containsKey("domD"));
        assertFalse(resultado.desglose().containsKey("ordN"));
    }

    @Test
    void turnoConDescansoNoRemuneradoDescuentaEsasHoras() {
        ConfigNomina cfg = ConfigNomina.porDefecto();

        // 8:00 a 17:00 (9h de permanencia) con 1h de almuerzo (12:00-13:00)
        // no remunerada → deben pagarse solo 8 horas, no 9.
        PayrollEngine.TurnoResultado resultado = engine.calcularTurno(
                cfg,
                LUNES_NO_FESTIVO,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                List.of()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> ordD = (Map<String, Object>) resultado.desglose().get("ordD");
        assertEquals(8.0, (Double) ordD.get("h"), 0.01,
                "El descanso de 1 hora debe descontarse; deben quedar 8 horas pagadas, no 9");
    }
}
