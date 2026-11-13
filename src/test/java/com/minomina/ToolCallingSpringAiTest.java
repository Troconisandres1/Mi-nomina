package com.minomina;

import com.minomina.controller.ChatController;
import com.minomina.tools.HerramientasAcademicas;
import com.minomina.tools.HerramientasNomina;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallingSpringAiTest {

    private final HerramientasAcademicas herramientasAcademicas = new HerramientasAcademicas();
    private final HerramientasNomina herramientasNomina = new HerramientasNomina();
    private final ChatController chatController = new ChatController(
            new ChatClient.Builder(), herramientasAcademicas, herramientasNomina);

    @Test
    @DisplayName("Prueba 1 y 2: @Tool calcularPromedio ejecuta metodo Java y calcula promedio exacto")
    void testCalcularPromedioTool() {
        String r1 = chatController.chat("Calcula el promedio de las notas 4.0, 3.5 y 4.5");
        assertTrue(r1.contains("4.00"), "El promedio de 4.0, 3.5 y 4.5 debe ser 4.00");

        String r2 = chatController.chat("Tengo las notas 2.5, 2.8 y 3.2. Calcula mi promedio.");
        assertTrue(r2.contains("2.83"), "El promedio de 2.5, 2.8 y 3.2 debe ser 2.83");
    }

    @Test
    @DisplayName("Prueba 3: @Tool estadoAcademico selecciona la herramienta correcta para promedio 2.8")
    void testEstadoAcademicoTool() {
        String r3 = chatController.chat("Un estudiante tiene promedio 2.8. ¿Cuál es su estado académico?");
        assertTrue(r3.contains("REPROBADO"), "Un promedio de 2.8 debe retornar REPROBADO");

        String rAprobado = chatController.chat("Un estudiante tiene promedio 3.8. ¿Cuál es su estado académico?");
        assertTrue(rAprobado.contains("APROBADO"), "Un promedio de 3.8 debe retornar APROBADO");
    }

    @Test
    @DisplayName("Actividad 1: @Tool calcularNotaFinal calcula promedio ponderado con 3 notas y 3 porcentajes")
    void testCalcularNotaFinalTool() {
        String r = chatController.chat(
                "Calcula la nota final definitiva ponderada con las notas 4.2 del 30%, 3.8 del 30% y 4.5 del 40%.");
        assertTrue(r.contains("4.20"), "4.2*0.3 + 3.8*0.3 + 4.5*0.4 = 4.20");
    }

    @Test
    @DisplayName("Actividad 2: @Tool estadoAsistencia determina habilitacion o reprobacion por asistencia")
    void testEstadoAsistenciaTool() {
        String ok = chatController.chat("Un estudiante registró un porcentaje de asistencia de 85%. ¿Cuál es su estado de asistencia?");
        assertTrue(ok.contains("HABILITADO POR ASISTENCIA"));

        String fail = chatController.chat("Un estudiante solo tuvo una asistencia de 72%. Verifica su estado de asistencia.");
        assertTrue(fail.contains("REPROBADO POR INASISTENCIA"));
    }

    @Test
    @DisplayName("Prueba 4 y Actividad 4: Consultas conceptuales responden sin invocar @Tool")
    void testPreguntaSinTool() {
        String r4 = chatController.chat("Explícame qué significa promedio académico.");
        assertTrue(r4.contains("promedio académico"));
        assertTrue(r4.contains("sin invocar ninguna herramienta"));
    }

    @Test
    @DisplayName("Extension Mi Nomina: @Tool calcularLiquidacionTurno y verificarTopeJornadaSemanal")
    void testHerramientasNominaTool() {
        String dom = chatController.chat("Con un salario base de 2000000, calcula el recargo de un turno dominical de 8 horas.");
        assertTrue(dom.contains("DOM"));
        assertTrue(dom.contains("1.9"));

        String tope = chatController.chat("Verifica el tope de la jornada semanal si un trabajador acumuló 46 horas semanales bajo la Ley 2101.");
        assertTrue(tope.contains("EXCEDE JORNADA ORDINARIA EN 4.0 HORAS EXTRAS"));
    }
}
