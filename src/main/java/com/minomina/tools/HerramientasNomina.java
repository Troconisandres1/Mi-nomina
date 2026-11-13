package com.minomina.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Herramientas de dominio de Mi Nómina expuestas al LLM mediante @Tool (Spring AI Tool Calling).
 * Permite que el asistente inteligente invoque cálculos deterministas de recargos laborales
 * bajo la Ley 2101 de 2021 (divisor 210 h) y Ley 2466 de 2025 (recargo dominical/festivo 90%).
 */
@Component
public class HerramientasNomina {

    private static final double DIVISOR_HORAS_MES_2026 = 210.0;

    @Tool(description = "Calcula el valor en pesos colombianos (COP) de un turno u hora extra según el salario base mensual, horas trabajadas y tipo de recargo (ORD, RN, HED, HEN, DOM, HEDF, HENF)")
    public Map<String, Object> calcularLiquidacionTurno(
            double salarioBase,
            double horas,
            String tipoTurno) {

        String codigo = tipoTurno == null ? "ORD" : tipoTurno.trim().toUpperCase(Locale.ROOT);
        System.out.println(
                ">>> TOOL calcularLiquidacionTurno ejecutada con: salarioBase="
                + salarioBase + ", horas=" + horas + ", tipoTurno=" + codigo);

        double valorHoraOrdinaria = salarioBase / DIVISOR_HORAS_MES_2026;
        double multiplicador = switch (codigo) {
            case "RN" -> 1.35;    // Recargo nocturno (+35%)
            case "HED" -> 1.25;   // Hora extra diurna (+25%)
            case "HEN" -> 1.75;   // Hora extra nocturna (+75%)
            case "DOM" -> 1.90;   // Dominical / festivo ordinario Ley 2466/2025 (+90%)
            case "HEDF" -> 2.15;  // Hora extra diurna dominical/festiva (+115%)
            case "HENF" -> 2.65;  // Hora extra nocturna dominical/festiva (+165%)
            default -> 1.00;      // Hora ordinaria diurna
        };

        double totalBruto = Math.round(valorHoraOrdinaria * multiplicador * horas);
        double deduccionSegSocial = Math.round(totalBruto * 0.08);
        double totalNeto = totalBruto - deduccionSegSocial;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("tipoTurno", codigo);
        res.put("horas", horas);
        res.put("valorHoraOrdinaria", Math.round(valorHoraOrdinaria));
        res.put("multiplicadorLegal", multiplicador);
        res.put("totalBrutoCOP", totalBruto);
        res.put("totalNetoCOP", totalNeto);
        return res;
    }

    @Tool(description = "Verifica si el total de horas trabajadas en la semana cumple el tope legal de 42 horas de la Ley 2101 de 2021")
    public String verificarTopeJornadaSemanal(double horasSemanales) {

        System.out.println(
                ">>> TOOL verificarTopeJornadaSemanal ejecutada con horasSemanales: "
                + horasSemanales);

        if (horasSemanales <= 42.0) {
            return String.format(Locale.US,
                    "CUMPLE LA LEY 2101 DE 2021 (%.1f h <= 42.0 h semanales ordinarias; margen disponible: %.1f h)",
                    horasSemanales, 42.0 - horasSemanales);
        } else if (horasSemanales <= 54.0) {
            return String.format(Locale.US,
                    "EXCEDE JORNADA ORDINARIA EN %.1f HORAS EXTRAS (dentro del tope máximo de 12 h extras semanales)",
                    horasSemanales - 42.0);
        } else {
            return String.format(Locale.US,
                    "ALERTA DE EXCESO LEGAL (%.1f h supera el límite máximo permitido de 42 h ordinarias + 12 h extras = 54 h/semana)",
                    horasSemanales);
        }
    }
}
