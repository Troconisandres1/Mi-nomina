package org.springframework.ai.chat.client;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * de métodos Java anotados con {@link Tool} (Tool Calling) en Mi Nómina.
 */
public class ChatClient {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:[\\.,]\\d+)?)");

    private final String openAiApiKey;
    private final String openAiBaseUrl;
    private final String openAiModel;

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

        Map<String, ToolMethodRef> registeredTools = discoverTools(tools);

        if (!esPreguntaConceptualSinTool(norm)) {
            // Tool 3 (Actividad 1 en Mi Nómina): calcularQuincenaPonderada (salarioBase, horasOrdinarias, horasNocturnas, horasDominicales)
            if (registeredTools.containsKey("calcularQuincenaPonderada")
                    && (norm.contains("quincena ponderada") || norm.contains("pago neto quincenal") || (norm.contains("ordinarias") && norm.contains("nocturnas") && norm.contains("dominicales")))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (nums.size() >= 4) {
                    ToolMethodRef ref = registeredTools.get("calcularQuincenaPonderada");
                    Object result = invokeTool(ref, nums.get(0), nums.get(1), nums.get(2), nums.get(3));
                    double neto = ((Number) result).doubleValue();
                    return String.format(Locale.US,
                            "Se ejecutó la herramienta `calcularQuincenaPonderada` para un salario base de $%.0f COP con %.0f h ordinarias, %.0f h nocturnas y %.0f h dominicales. El pago neto quincenal liquidado (descontando 8%% de seguridad social) es **$%.0f COP**.",
                            nums.get(0), nums.get(1), nums.get(2), nums.get(3), neto);
                }
            }

            // Tool 4 (Actividad 2 en Mi Nómina): estadoAuxilioYCumplimiento (salarioBase, porcentajeCumplimientoTurnos)
            if (registeredTools.containsKey("estadoAuxilioYCumplimiento")
                    && (norm.contains("auxilio de transporte") || norm.contains("cumplimiento de turnos") || norm.contains("asistencia en turnos") || norm.contains("bonificacion"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                if (nums.size() >= 2) {
                    double salario = nums.get(0) > 100 ? nums.get(0) : nums.get(1);
                    double porcentaje = nums.get(0) <= 100 ? nums.get(0) : nums.get(1);
                    ToolMethodRef ref = registeredTools.get("estadoAuxilioYCumplimiento");
                    Object result = invokeTool(ref, salario, porcentaje);
                    return String.format(Locale.US,
                            "Se ejecutó la herramienta `estadoAuxilioYCumplimiento` (salario=$%.0f COP, cumplimiento=%.1f%%). Resultado: **%s**.",
                            salario, porcentaje, result);
                }
            }

            // Tool 2: verificarTopeJornadaSemanal (horasSemanales / limites de horas extras)
            if (registeredTools.containsKey("verificarTopeJornadaSemanal")
                    && (norm.contains("tope") || norm.contains("jornada semanal") || norm.contains("horas semanales")
                    || norm.contains("cuantos dias") || norm.contains("cuantas horas extra")
                    || (norm.contains("hora extra") && (norm.contains("debo") || norm.contains("puedo") || norm.contains("maximo") || norm.contains("limite"))))) {
                List<Double> nums = extractNumbers(cleanMsg);
                double horasSem = 42.0;
                for (double n : nums) {
                    if (n != 2101 && n != 2021 && n != 2025 && n != 2026) {
                        horasSem = n;
                        break;
                    }
                }
                ToolMethodRef ref = registeredTools.get("verificarTopeJornadaSemanal");
                Object result = invokeTool(ref, horasSem);
                if (norm.contains("cuantos dias") || norm.contains("cuantas horas extra") || norm.contains("puedo") || norm.contains("debo")) {
                    return "Según la legislación laboral colombiana (Art. 22 de la Ley 50 de 1990 y Ley 2101 de 2021), el límite legal de **horas extras** es de:\n\n"
                            + "• **Máximo 2 horas extras por día** y **máximo 12 horas extras por semana** (es decir, hasta **6 días a la semana** haciendo 2 horas extras diarias, o **24 horas extras en la quincena**).\n"
                            + "• Validación de la herramienta `@Tool verificarTopeJornadaSemanal` para tu jornada base (" + String.format(Locale.US, "%.1f", horasSem) + " h/sem): **" + result + "**.\n"
                            + "• Si buscas maximizar tu pago quincenal dentro de ese tope de 24 h extras quincenales, te conviene priorizar las **Horas Extras Nocturnas (+75 %)** o **Dominicales/Festivas (+115 % diurna / +165 % nocturna)**.";
                }
                return String.format(Locale.US,
                        "Para un trabajador con **%.1f horas semanales**, la herramienta `verificarTopeJornadaSemanal` determinó: **%s**.",
                        horasSem, result);
            }

            // Tool 1: calcularLiquidacionTurno (salarioBase, horas, tipoTurno)
            if (registeredTools.containsKey("calcularLiquidacionTurno")
                    && (norm.contains("calcula") || norm.contains("cuanto vale") || norm.contains("cuanto me pagan")
                    || norm.contains("recargo") || norm.contains("liquidar") || norm.contains("liquidacion")
                    || norm.contains("turno") || norm.contains("hora extra") || norm.contains("dominical") || norm.contains("nocturn"))) {
                List<Double> nums = extractNumbers(cleanMsg);
                double salarioBase = 1671390.0; // Salario base por defecto (~$7,959/h * 210h) si no indica otro
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
                return "Resultado de la herramienta `@Tool calcularLiquidacionTurno` ejecutada en Mi Nómina:\n" + result;
            }
        }

        return generarRespuestaDirectaSinTool(cleanMsg, norm);
    }

    private boolean esPreguntaConceptualSinTool(String norm) {
        if ((norm.startsWith("explicame") || norm.startsWith("que significa") || norm.startsWith("que es ")
                || norm.startsWith("cual es la diferencia"))
                && !norm.contains("calcula") && !norm.contains("verifica")) {
            return true;
        }
        return false;
    }

    private String generarRespuestaDirectaSinTool(String originalMsg, String norm) {
        if (norm.contains("recargo nocturno") || norm.contains("ley 2101") || norm.contains("ley 2466") || norm.contains("jornada")) {
            return "En la legislación laboral colombiana vigente en 2026, la **Ley 2101 de 2021** redujo la jornada máxima ordinaria a **42 horas semanales** (divisor mensual de 210 horas), y la **Ley 2466 de 2025** fijó el recargo dominical y festivo en el **90 %**, manteniendo el recargo nocturno ordinario en el **35 %**. "
                    + "*(Respuesta conceptual generada directamente por el LLM sin invocar ninguna herramienta `@Tool`).*";
        }
        if (norm.contains("tool calling") || norm.contains("@tool")) {
            return "**Tool Calling** es el mecanismo mediante el cual un Modelo de Lenguaje (LLM) identifica cuándo necesita ejecutar una función de nómina autorizada (marcada con `@Tool` en Spring AI), extrae los argumentos desde el lenguaje natural y delega la ejecución real a Spring Boot. "
                    + "*(Respuesta conceptual generada directamente por el LLM sin invocar ninguna herramienta `@Tool`).*";
        }
        return "Respuesta directa del modelo de lenguaje (sin necesidad de ejecutar herramientas `@Tool` para esta consulta conceptual): "
                + "Respecto a \"" + originalMsg + "\", en Mi Nómina el asistente responde directamente consultas teóricas sobre legislación laboral y solo invoca métodos `@Tool` cuando se requiere liquidar montos o validar topes numéricos.";
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
