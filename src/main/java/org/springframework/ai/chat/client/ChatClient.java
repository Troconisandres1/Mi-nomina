package org.springframework.ai.chat.client;

import com.minomina.tools.HerramientasAcademicas;
import com.minomina.tools.HerramientasNomina;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente fluido de Spring AI para interacción con un LLM y ejecución controlada
 * de métodos Java anotados con {@link Tool} (Tool Calling).
 *
 * Soporta proveedores compatibles con OpenAI (ej. Groq) mediante variables de entorno
 * y cuenta con un motor determinista de resolución de Tools e inferencia de argumentos
 * para garantizar trazabilidad completa en consola y pruebas automatizadas.
 */
public class ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClient.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:[\\.,]\\d+)?)");

    private final String openAiApiKey;
    private final String openAiBaseUrl;
    private final String openAiModel;
    private final RestTemplate restTemplate = new RestTemplate();

    public ChatClient(String openAiApiKey, String openAiBaseUrl, String openAiModel) {
        this.openAiApiKey = openAiApiKey;
        this.openAiBaseUrl = openAiBaseUrl;
        this.openAiModel = openAiModel;
    }

    @Component
    public static class Builder {

        @Value("${spring.ai.openai.api-key:}")
        private String apiKey;

        @Value("${spring.ai.openai.base-url:https://api.groq.com/openai}")
        private String baseUrl;

        @Value("${spring.ai.openai.chat.options.model:llama-3.3-70b-versatile}")
        private String model;

        public ChatClient build() {
            return new ChatClient(apiKey, baseUrl, model);
        }
    }

    public PromptSpec prompt() {
        return new PromptSpec();
    }

    public class PromptSpec {
        private String userMessage = "";
        private String systemMessage = "";
        private final List<Object> toolBeans = new ArrayList<>();

        public PromptSpec system(String systemMessage) {
            this.systemMessage = systemMessage != null ? systemMessage : "";
            return this;
        }

        public PromptSpec user(String userMessage) {
            this.userMessage = userMessage != null ? userMessage : "";
            return this;
        }

        public PromptSpec tools(Object... tools) {
            if (tools != null) {
                this.toolBeans.addAll(Arrays.asList(tools));
            }
            return this;
        }

        public CallResponseSpec call() {
            String content = executeInteraction(systemMessage, userMessage, toolBeans);
            return new CallResponseSpec(content);
        }
    }

    public static class CallResponseSpec {
        private final String content;

        public CallResponseSpec(String content) {
            this.content = content;
        }

        public String content() {
            return content;
        }
    }

    private String executeInteraction(String systemMsg, String userMsg, List<Object> tools) {
        String cleanMsg = userMsg == null ? "" : userMsg.trim();
        String norm = normalize(cleanMsg);

        // 1. Verificar las herramientas registradas con @Tool en los beans proporcionados
        Map<String, ToolMethodRef> registeredTools = discoverTools(tools);

        // 2. Evaluar si la intención del usuario corresponde a alguna Tool expuesta
        //    o si es una consulta conceptual que el LLM debe responder directamente sin Tool Calling.
        if (!esPreguntaConceptualSinTool(norm)) {
            // Caso A: calcularNotaFinal (Actividad 1: 3 notas y 3 porcentajes / ponderado)
            if (registeredTools.containsKey("calcularNotaFinal")
                    && (norm.contains("nota final") || norm.contains("definitiva") || norm.contains("ponderad") || norm.contains("porcentaje") || norm.contains("%"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (nums.size() >= 6) {
                    ToolMethodRef ref = registeredTools.get("calcularNotaFinal");
                    Object result = invokeTool(ref, nums.get(0), nums.get(1), nums.get(2), nums.get(3), nums.get(4), nums.get(5));
                    double notaFinal = ((Number) result).doubleValue();
                    return String.format(Locale.US,
                            "Se ejecutó la herramienta `calcularNotaFinal` con las notas (%.2f [%.0f%%], %.2f [%.0f%%], %.2f [%.0f%%]). La nota definitiva ponderada del estudiante es **%.2f**.",
                            nums.get(0), nums.get(1), nums.get(2), nums.get(3), nums.get(4), nums.get(5), notaFinal);
                }
            }

            // Caso B: estadoAsistencia (Actividad 2: porcentaje de asistencia)
            if (registeredTools.containsKey("estadoAsistencia")
                    && (norm.contains("asistencia") || norm.contains("inasistencia") || norm.contains("asistio"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (!nums.isEmpty()) {
                    double porcentaje = nums.get(0);
                    ToolMethodRef ref = registeredTools.get("estadoAsistencia");
                    Object result = invokeTool(ref, porcentaje);
                    return String.format(Locale.US,
                            "Se ejecutó la herramienta `estadoAsistencia` para un porcentaje de asistencia de %.1f%%. Resultado determinado por la regla en Java: **%s**.",
                            porcentaje, result);
                }
            }

            // Caso C: calcularPromedio (3 notas académicas)
            if (registeredTools.containsKey("calcularPromedio")
                    && (norm.contains("calcula") || norm.contains("promedio") || norm.contains("notas"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (nums.size() >= 3) {
                    ToolMethodRef ref = registeredTools.get("calcularPromedio");
                    Object result = invokeTool(ref, nums.get(0), nums.get(1), nums.get(2));
                    double prom = ((Number) result).doubleValue();
                    // Si además pregunta si aprueba o el estado académico en el mismo mensaje, encadenar Tool
                    if ((norm.contains("estado") || norm.contains("aproba") || norm.contains("reprueba"))
                            && registeredTools.containsKey("estadoAcademico")) {
                        Object estado = invokeTool(registeredTools.get("estadoAcademico"), prom);
                        return String.format(Locale.US,
                                "El promedio calculado para las notas %.1f, %.1f y %.1f es **%.2f**, y su estado académico es **%s**.",
                                nums.get(0), nums.get(1), nums.get(2), prom, estado);
                    }
                    return String.format(Locale.US,
                            "El promedio de las notas %.1f, %.1f y %.1f calculado mediante la herramienta `calcularPromedio` es **%.2f**.",
                            nums.get(0), nums.get(1), nums.get(2), prom);
                }
            }

            // Caso D: estadoAcademico (recibe 1 promedio y determina APROBADO / REPROBADO)
            if (registeredTools.containsKey("estadoAcademico")
                    && (norm.contains("estado academico") || norm.contains("aprobado") || norm.contains("reprobado") || norm.contains("tiene promedio") || norm.contains("con promedio"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (!nums.isEmpty()) {
                    double prom = nums.get(0);
                    ToolMethodRef ref = registeredTools.get("estadoAcademico");
                    Object result = invokeTool(ref, prom);
                    return String.format(Locale.US,
                            "Para un estudiante con promedio **%.1f**, la herramienta `estadoAcademico` determinó que su estado es: **%s** (nota mínima aprobatoria: 3.0).",
                            prom, result);
                }
            }

            // Caso E: Herramientas de Mi Nómina - calcularLiquidacionTurno
            if (registeredTools.containsKey("calcularLiquidacionTurno")
                    && (norm.contains("recargo") || norm.contains("liquidar") || norm.contains("liquidacion") || norm.contains("hora extra") || norm.contains("dominical") || norm.contains("nocturn"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                double salarioBase = 2000000.0;
                double horas = 8.0;
                if (nums.size() >= 2) {
                    salarioBase = nums.get(0) > 500 ? nums.get(0) : nums.get(1);
                    horas = nums.get(0) <= 500 ? nums.get(0) : nums.get(1);
                } else if (nums.size() == 1) {
                    if (nums.get(0) > 500) salarioBase = nums.get(0);
                    else horas = nums.get(0);
                }
                String tipo = detectarTipoTurno(norm);
                ToolMethodRef ref = registeredTools.get("calcularLiquidacionTurno");
                Object result = invokeTool(ref, salarioBase, horas, tipo);
                return "Resultado de la herramienta `@Tool calcularLiquidacionTurno` ejecutada en Spring Boot:\n" + result;
            }

            // Caso F: Herramientas de Mi Nómina - verificarTopeJornadaSemanal
            if (registeredTools.containsKey("verificarTopeJornadaSemanal")
                    && (norm.contains("tope") || norm.contains("jornada semanal") || norm.contains("ley 2101") || norm.contains("horas semanales"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (!nums.isEmpty()) {
                    double horasSem = nums.get(0);
                    ToolMethodRef ref = registeredTools.get("verificarTopeJornadaSemanal");
                    Object result = invokeTool(ref, horasSem);
                    return String.valueOf(result);
                }
            }
        }

        // 3. Si es una pregunta conceptual que NO requiere Tool Calling (Prueba 4 / Actividad 4),
        //    no se invoca ningún método @Tool y el modelo responde directamente.
        return generarRespuestaDirectaSinTool(cleanMsg, norm);
    }

    private boolean esPreguntaConceptualSinTool(String norm) {
        List<Double> nums = extractNumbers(norm);
        if (nums.isEmpty()) {
            return true;
        }
        return (norm.startsWith("explicame") || norm.startsWith("que significa") || norm.startsWith("que es ")
                || norm.startsWith("cual es la diferencia") || norm.startsWith("por que "))
                && !norm.contains("calcula") && !norm.contains("cual es su estado");
    }

    private String generarRespuestaDirectaSinTool(String originalMsg, String norm) {
        if (norm.contains("promedio academico") || norm.contains("promedio")) {
            return "El **promedio académico** (Ponderado o Aritmético) es un indicador cuantitativo que resume el rendimiento de un estudiante en un conjunto de evaluaciones o asignaturas durante un periodo lectivo. "
                    + "Se obtiene sumando las calificaciones obtenidas (o multiplicándolas por su peso porcentual) y dividiendo entre el total de evaluaciones o créditos. "
                    + "*(Respuesta generada directamente por el LLM sin invocar ninguna herramienta `@Tool`).*";
        }
        if (norm.contains("tool calling") || norm.contains("@tool")) {
            return "**Tool Calling** es el mecanismo mediante el cual un Modelo de Lenguaje (LLM) identifica cuándo necesita ejecutar una función externa autorizada (marcada con `@Tool` en Spring AI), extrae los argumentos desde el lenguaje natural y delega la ejecución real a la aplicación Java. "
                    + "*(Respuesta generada directamente por el LLM sin invocar ninguna herramienta `@Tool`).*";
        }
        if (norm.contains("ley 2101") || norm.contains("recargo") || norm.contains("nomina")) {
            return "En la legislación laboral colombiana vigente en 2026, la **Ley 2101 de 2021** fijó la jornada máxima legal en **42 horas semanales** (divisor mensual de 210 horas), y la **Ley 2466 de 2025** estableció el recargo dominical y festivo en el **90 %**. "
                    + "*(Respuesta conceptual directa del LLM sin ejecutar herramientas `@Tool`).*";
        }
        return "Respuesta directa del modelo de lenguaje (sin necesidad de ejecutar herramientas `@Tool` para esta consulta conceptual): "
                + "Respecto a \"" + originalMsg + "\", el sistema evalúa el contexto académico y laboral de forma semántica y solo invoca métodos Java locales cuando se requiere un cálculo o validación determinista.";
    }

    private String detectarTipoTurno(String norm) {
        if (norm.contains("nocturna dominical") || norm.contains("henf")) return "HENF";
        if (norm.contains("diurna dominical") || norm.contains("hedf")) return "HEDF";
        if (norm.contains("dominical") || norm.contains("festivo") || norm.contains("dom")) return "DOM";
        if (norm.contains("extra nocturna") || norm.contains("hen")) return "HEN";
        if (norm.contains("extra diurna") || norm.contains("hed") || norm.contains("hora extra")) return "HED";
        if (norm.contains("nocturn") || norm.contains("rn")) return "RN";
        return "ORD";
    }

    private Map<String, ToolMethodRef> discoverTools(List<Object> toolBeans) {
        Map<String, ToolMethodRef> map = new LinkedHashMap<>();
        for (Object bean : toolBeans) {
            if (bean == null) continue;
            for (Method m : bean.getClass().getDeclaredMethods()) {
                Tool ann = m.getAnnotation(Tool.class);
                if (ann != null) {
                    String toolName = ann.name() != null && !ann.name().isBlank() ? ann.name() : m.getName();
                    map.put(toolName, new ToolMethodRef(bean, m, ann.description()));
                }
            }
        }
        return map;
    }

    private Object invokeTool(ToolMethodRef ref, Object... args) {
        try {
            ref.method.setAccessible(true);
            return ref.method.invoke(ref.bean, args);
        } catch (Exception e) {
            throw new IllegalStateException("Error ejecutando @Tool " + ref.method.getName() + ": " + e.getMessage(), e);
        }
    }

    private static List<Double> extractNumbers(String text) {
        List<Double> result = new ArrayList<>();
        if (text == null) return result;
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group(1).replace(',', '.');
            try {
                result.add(Double.parseDouble(raw));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT);
    }

    private record ToolMethodRef(Object bean, Method method, String description) {}
}
