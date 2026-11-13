package com.minomina;

import com.minomina.controller.ChatController;
import com.minomina.tools.HerramientasNomina;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallingSpringAiTest {

    private final HerramientasNomina herramientasNomina = new HerramientasNomina();
    private final ChatController chatController = new ChatController(
            new ChatClient.Builder(), herramientasNomina);

    @Test
    @DisplayName("Prueba 1 y 2: @Tool calcularLiquidacionTurno ejecuta metodo Java y liquida recargo exacto en COP")
    void testCalcularLiquidacionTurnoTool() {
        String r1 = chatController.chat("Con un salario base de 2000000, calcula el recargo de un turno dominical de 8 horas.");
        assertTrue(r1.contains("DOM"));
        assertTrue(r1.contains("144762"));

        String r2 = chatController.chat("Tengo un salario base de 2500000 y trabajé 4 horas extra nocturna. Calcula la liquidación del turno.");
        assertTrue(r2.contains("HEN"));
        assertTrue(r2.contains("1.75"));
    }

    @Test
    @DisplayName("Prueba 3: @Tool verificarTopeJornadaSemanal selecciona la herramienta correcta segun Ley 2101")
    void testVerificarTopeJornadaSemanalTool() {
        String r3 = chatController.chat("Un trabajador acumuló 46 horas semanales. Verifica el tope de la jornada semanal.");
        assertTrue(r3.contains("JORNADA CON HORAS EXTRAS"));

        String rCumple = chatController.chat("Un trabajador acumuló 40 horas semanales. Verifica el tope de la jornada semanal.");
        assertTrue(rCumple.contains("DENTRO DEL TOPE ORDINARIO LEGAL"));
    }

    @Test
    @DisplayName("Actividad 1: @Tool calcularQuincenaPonderada calcula neto quincenal con horas ordinarias, nocturnas y dominicales")
    void testCalcularQuincenaPonderadaTool() {
        String r = chatController.chat(
                "Calcula el pago neto quincenal ponderado con salario base de 2000000 para 60 horas ordinarias, 20 horas nocturnas y 16 horas dominicales.");
        assertTrue(r.contains("1028648"));
    }

    @Test
    @DisplayName("Actividad 2: @Tool estadoAuxilioYCumplimiento evalua tope de 2 SMMLV y asistencia a turnos")
    void testEstadoAuxilioYCumplimientoTool() {
        String ok = chatController.chat("Un trabajador tiene salario de 1800000 y un cumplimiento de turnos de 92%. ¿Cuál es su estado de auxilio de transporte y bonificación?");
        assertTrue(ok.contains("APLICA AUXILIO DE TRANSPORTE LEGAL"));
        assertTrue(ok.contains("HABILITADO PARA BONIFICACIÓN DE TURNOS"));

        String noAux = chatController.chat("Un trabajador con salario de 3200000 tuvo una asistencia en turnos de 75%. Verifica su auxilio de transporte y bonificación.");
        assertTrue(noAux.contains("NO APLICA AUXILIO DE TRANSPORTE"));
        assertTrue(noAux.contains("SIN BONIFICACIÓN POR INASISTENCIA EN TURNOS"));
    }

    @Test
    @DisplayName("Prueba 4 y Actividad 4: Consultas conceptuales sobre Ley 2101 o Tool Calling responden sin invocar @Tool")
    void testPreguntaSinTool() {
        String r4 = chatController.chat("Explícame qué significa el recargo nocturno y la jornada máxima bajo la Ley 2101 de 2021.");
        assertTrue(r4.contains("Ley 2101 de 2021"));
        assertTrue(r4.contains("sin invocar ninguna herramienta"));
    }
}
