package com.minomina.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Herramientas de dominio de Mi Nómina expuestas al LLM mediante @Tool (Spring AI Tool Calling).
 * Permite que el modelo de lenguaje seleccione e invoque métodos Java deterministas para:
 * 1. Calcular la liquidación de un turno según su recargo legal (calcularLiquidacionTurno).
 * 2. Verificar el cumplimiento del tope semanal de 42 h de la Ley 2101 de 2021 (verificarTopeJornadaSemanal).
 * 3. Calcular la nómina quincenal ponderada por tipos de hora y recargos (calcularQuincenaPonderada).
 * 4. Determinar si el trabajador aplica a Auxilio de Transporte y bonificación de cumplimiento (estadoAuxilioYCumplimiento).
 */
@Component
public class HerramientasNomina {

    private static final double DIVISOR_HORAS_MES_2026 = 210.0;
    private static final double SMMLV_2026_REFERENCIA = 1423500.0;

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

    @Tool(description = "Determina si las horas semanales trabajadas cumplen o exceden el tope legal de 42 horas de la Ley 2101 de 2021")
    public String verificarTopeJornadaSemanal(double horasSemanales) {

        System.out.println(
                ">>> TOOL verificarTopeJornadaSemanal ejecutada con horasSemanales: "
                + horasSemanales);

        if (horasSemanales <= 42.0) {
            return String.format(Locale.US,
                    "DENTRO DEL TOPE ORDINARIO LEGAL (%.1f h <= 42.0 h semanales; margen ordinario restante: %.1f h)",
                    horasSemanales, 42.0 - horasSemanales);
        } else if (horasSemanales <= 54.0) {
            return String.format(Locale.US,
                    "JORNADA CON HORAS EXTRAS (Excede la jornada ordinaria en %.1f h extras, dentro del límite legal de 12 h extras/semana)",
                    horasSemanales - 42.0);
        } else {
            return String.format(Locale.US,
                    "ALERTA DE EXCESO LEGAL (%.1f h supera el máximo permitido de 42 h ordinarias + 12 h extras = 54 h/semana)",
                    horasSemanales);
        }
    }

    @Tool(description = "Calcula el pago neto quincenal ponderado a partir del salario base y las horas ordinarias, nocturnas y dominicales trabajadas")
    public double calcularQuincenaPonderada(
            double salarioBase,
            double horasOrdinarias,
            double horasNocturnas,
            double horasDominicales) {

        System.out.println(
                ">>> TOOL calcularQuincenaPonderada ejecutada con: salarioBase="
                + salarioBase + ", horasOrdinarias=" + horasOrdinarias
                + ", horasNocturnas=" + horasNocturnas
                + ", horasDominicales=" + horasDominicales);

        double valorHora = salarioBase / DIVISOR_HORAS_MES_2026;
        double bruto = (horasOrdinarias * valorHora * 1.00)
                + (horasNocturnas * valorHora * 1.35)
                + (horasDominicales * valorHora * 1.90);
        double neto = bruto * (1.0 - 0.08); // Menos 8% Salud y Pensión
        return Math.round(neto);
    }

    @Tool(description = "Determina si un trabajador tiene derecho a auxilio de transporte legal y bonificación según su salario base y porcentaje de cumplimiento de turnos")
    public String estadoAuxilioYCumplimiento(double salarioBase, double porcentajeCumplimientoTurnos) {

        System.out.println(
                ">>> TOOL estadoAuxilioYCumplimiento ejecutada con: salarioBase="
                + salarioBase + ", cumplimiento=" + porcentajeCumplimientoTurnos + "%");

        boolean aplicaAuxilio = salarioBase <= (2.0 * SMMLV_2026_REFERENCIA);
        boolean cumpleTurnos = porcentajeCumplimientoTurnos >= 85.0;

        String estadoAuxilio = aplicaAuxilio
                ? "APLICA AUXILIO DE TRANSPORTE LEGAL (Salario <= 2 SMMLV)"
                : "NO APLICA AUXILIO DE TRANSPORTE (Salario > 2 SMMLV)";
        String estadoCumplimiento = cumpleTurnos
                ? "HABILITADO PARA BONIFICACIÓN DE TURNOS (Cumplimiento >= 85%)"
                : "SIN BONIFICACIÓN POR INASISTENCIA EN TURNOS (Cumplimiento < 85%)";

        return estadoAuxilio + " | " + estadoCumplimiento;
    }
}
