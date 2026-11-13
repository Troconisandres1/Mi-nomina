package com.minomina.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Herramientas expuestas al LLM mediante Spring AI Tool Calling (@Tool).
 * Incluye las dos herramientas base de la guía más las extensiones
 * solicitadas en la Actividad 1 (calcularNotaFinal) y Actividad 2 (estadoAsistencia).
 */
@Component
public class HerramientasAcademicas {

    @Tool(description = "Calcula el promedio de tres notas académicas")
    public double calcularPromedio(
            double nota1,
            double nota2,
            double nota3) {

        System.out.println(
                ">>> TOOL calcularPromedio ejecutada con: "
                + nota1 + ", " + nota2 + ", " + nota3);

        return (nota1 + nota2 + nota3) / 3.0;
    }

    @Tool(description =
            "Determina si un estudiante está aprobado o reprobado según su promedio")
    public String estadoAcademico(double promedio) {

        System.out.println(
                ">>> TOOL estadoAcademico ejecutada con promedio: "
                + promedio);

        return promedio >= 3.0 ? "APROBADO" : "REPROBADO";
    }

    /**
     * Actividad 1 - Nueva herramienta:
     * Recibe tres notas y sus respectivos porcentajes (ej. 30, 30, 40 o 0.3, 0.3, 0.4)
     * y devuelve la nota definitiva ponderada.
     */
    @Tool(description =
            "Calcula la nota final definitiva ponderada a partir de tres notas y sus respectivos porcentajes")
    public double calcularNotaFinal(
            double nota1, double porcentaje1,
            double nota2, double porcentaje2,
            double nota3, double porcentaje3) {

        System.out.println(
                ">>> TOOL calcularNotaFinal ejecutada con: notas=("
                + nota1 + ", " + nota2 + ", " + nota3 + "), porcentajes=("
                + porcentaje1 + "%, " + porcentaje2 + "%, " + porcentaje3 + "%)");

        double p1 = porcentaje1 > 1.0 ? porcentaje1 / 100.0 : porcentaje1;
        double p2 = porcentaje2 > 1.0 ? porcentaje2 / 100.0 : porcentaje2;
        double p3 = porcentaje3 > 1.0 ? porcentaje3 / 100.0 : porcentaje3;

        double definitiva = (nota1 * p1) + (nota2 * p2) + (nota3 * p3);
        return Math.round(definitiva * 100.0) / 100.0;
    }

    /**
     * Actividad 2 - Herramienta de asistencia:
     * Recibe el porcentaje de asistencia (0 a 100) y retorna el estado académico por asistencia.
     * Regla institucional: >= 80% HABILITADO POR ASISTENCIA, < 80% REPROBADO POR INASISTENCIA.
     */
    @Tool(description =
            "Determina el estado académico de asistencia de un estudiante según su porcentaje de asistencia")
    public String estadoAsistencia(double porcentajeAsistencia) {

        System.out.println(
                ">>> TOOL estadoAsistencia ejecutada con porcentaje: "
                + porcentajeAsistencia + "%");

        if (porcentajeAsistencia >= 80.0) {
            return "HABILITADO POR ASISTENCIA (Cumple el mínimo del 80%)";
        } else {
            return "REPROBADO POR INASISTENCIA (Inferior al mínimo del 80%)";
        }
    }
}
