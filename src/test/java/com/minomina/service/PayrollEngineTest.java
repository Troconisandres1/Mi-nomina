package com.minomina.service;

import com.minomina.model.ConfigNomina;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de la capa Service (lógica de negocio pura, sin levantar el
 * contexto de Spring — más rápida y no depende de base de datos).
 * Cubre el caso más simple del motor de nómina: un turno ordinario diurno,
 * un día hábil cualquiera, sin extras ni recargos.
 */
class PayrollEngineTest {

    // 5 de enero de 2026 es lunes, no está en la lista de festivos, y es
    // anterior a los cortes legales de julio de 2026 (tarifas base vigentes:
    // hora ordinaria $7.959, jornada de 44h semanales).
    private static final LocalDate LUNES_NO_FESTIVO = LocalDate.of(2026, 1, 5);

    private final PayrollEngine engine = new PayrollEngine(new FestivosService());

    @Test
    void confirmaQueLaFechaDePruebaEsUnLunes() {
        // Salvaguarda: si esta aserción falla, el resto del test pierde sentido
        // (dejaría de ser un turno "ordinario" simple).
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
