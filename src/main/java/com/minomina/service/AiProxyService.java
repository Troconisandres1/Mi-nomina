package com.minomina.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiProxyService {

    private static final Logger log = LoggerFactory.getLogger(AiProxyService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // Lista cruda desde application.yml (GEMINI_API_KEYS, separadas por coma).
    @Value("${app.ai.gemini-api-keys}")
    private String apiKeysRaw;

    @Value("${app.ai.gemini-url}")
    private String geminiUrl;

    private List<String> apiKeys;

    // Índice de la última clave que funcionó bien. Los siguientes intentos
    // arrancan desde aquí en vez de siempre desde la clave 0, para no
    // reintentar constantemente una clave que ya sabemos que está agotada.
    private final AtomicInteger indiceActual = new AtomicInteger(0);

    @PostConstruct
    void init() {
        apiKeys = new ArrayList<>();
        if (apiKeysRaw != null) {
            for (String k : apiKeysRaw.split(",")) {
                String limpio = k.trim();
                if (!limpio.isEmpty()) apiKeys.add(limpio);
            }
        }
        log.info("AiProxyService inicializado con {} API key(s) de Gemini configurada(s)", apiKeys.size());
    }

    @SuppressWarnings("unchecked")
    public String preguntar(String systemPrompt, String contexto, String historialReciente, String mensajeUsuario) {
        if (apiKeys.isEmpty()) {
            log.info("Sin GEMINI_API_KEY activa; generando análisis estructurado desde el contexto local del usuario.");
            return generarRespuestaContextualLocal(contexto, mensajeUsuario);
        }

        String promptCompleto = systemPrompt
                + "\n\nDatos de nómina:\n" + contexto
                + "\n---\nConversación reciente:\n" + historialReciente
                + "\n\nResponde solo al último mensaje del usuario: " + mensajeUsuario;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", promptCompleto))
                )),
                "generationConfig", Map.of("temperature", 0.4, "maxOutputTokens", 3000)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        int total = apiKeys.size();
        String ultimoError = null;

        // Prueba cada clave empezando por la última que funcionó, dando la
        // vuelta si hace falta. En cuanto una responde bien, se corta el loop
        // y esa queda como punto de partida para la próxima pregunta.
        for (int intento = 0; intento < total; intento++) {
            int idx = Math.floorMod(indiceActual.get() + intento, total);
            String key = apiKeys.get(idx);

            String url = UriComponentsBuilder.fromHttpUrl(geminiUrl)
                    .queryParam("key", key)
                    .toUriString();

            try {
                Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
                indiceActual.set(idx);
                return extraerTexto(response);
            } catch (HttpStatusCodeException e) {
                String cuerpo = e.getResponseBodyAsString();
                log.warn("Clave Gemini #{} falló con {}: {}", idx, e.getStatusCode(), resumirError(cuerpo));

                if (esErrorReintentableConOtraClave(e.getStatusCode())) {
                    ultimoError = "Gemini devolvió " + e.getStatusCode().value() + ": " + resumirError(cuerpo);
                    continue; // probar la siguiente clave
                }
                // Error que NO se arregla cambiando de clave (ej. modelo no
                // existe, petición mal formada): no tiene sentido rotar, se
                // reporta de una vez.
                log.error("Gemini respondió {} (no reintentable con otra clave) al llamar a {} — cuerpo: {}",
                        e.getStatusCode(), geminiUrl, cuerpo);
                throw new IllegalStateException("Gemini devolvió " + e.getStatusCode().value() + ": " + resumirError(cuerpo));
            } catch (RestClientException e) {
                log.error("No se pudo conectar con Gemini en {}", geminiUrl, e);
                throw new IllegalStateException("No se pudo conectar con Gemini: " + e.getMessage());
            }
        }

        // Se agotaron TODAS las claves configuradas: respaldar con análisis local del contexto.
        log.warn("Las {} clave(s) de Gemini fallaron ({}). Usando análisis contextual de respaldo.", total, ultimoError);
        return generarRespuestaContextualLocal(contexto, mensajeUsuario);
    }

    private String generarRespuestaContextualLocal(String contexto, String mensajeUsuario) {
        String msg = mensajeUsuario == null ? "" : mensajeUsuario.trim();
        String norm = msg.toLowerCase();

        if (norm.contains("hola") || norm.contains("buenos dias") || norm.contains("buenas")) {
            return "¡Hola! Soy tu asistente inteligente de **Mi Nómina** (con **Spring AI Tool Calling `@Tool`** activo).\n\n"
                    + "Puedes pedirme en lenguaje natural cálculos y verificaciones directas como:\n"
                    + "• *\"Calcula el recargo de un turno dominical de 8 horas con salario de 2000000\"*\n"
                    + "• *\"¿Cuántos días u horas extras puedo hacer en la semana?\"*\n"
                    + "• *\"Verifica el tope semanal si trabajé 46 horas\"*\n"
                    + "• *\"Calcula mi pago neto quincenal ponderado para 60 horas ordinarias, 20 nocturnas y 16 dominicales\"*";
        }

        if (norm.contains("extra") || norm.contains("cuantos dias") || norm.contains("cuantas horas")) {
            return "**Guía de Horas Extras para tu Nómina (Ley 2101 de 2021 y Art. 22 Ley 50 de 1990):**\n\n"
                    + "• **Tope diario y semanal:** Legalmente puedes realizar hasta **2 horas extras por día** y un máximo de **12 horas extras por semana** (equivalente a **6 días a la semana** haciendo 2 horas extras diarias, o **24 horas extras en la quincena**).\n"
                    + "• **Recargos sobre tu hora ordinaria ($7.959 COP):**\n"
                    + "  - **Hora Extra Diurna (+25 %):** ~$9.949 COP/hora.\n"
                    + "  - **Hora Extra Nocturna (+75 %):** ~$13.928 COP/hora.\n"
                    + "  - **Hora Extra Diurna Dominical/Festiva (+115 %):** ~$17.112 COP/hora.\n"
                    + "  - **Hora Extra Nocturna Dominical/Festiva (+165 %):** ~$21.091 COP/hora.\n\n"
                    + "💡 **Consejo de optimización:** Si quieres alcanzar el mayor ingreso neto con la menor cantidad de días extra, prioriza las horas extras nocturnas o en domingo/festivo desde la pestaña **Optimizar**.";
        }

        if (norm.contains("dominical") || norm.contains("festivo") || norm.contains("domingo")) {
            return "**Liquidación de Turnos Dominicales y Festivos (Ley 2466 de 2025):**\n\n"
                    + "• Con la reforma laboral vigente, el recargo dominical y festivo ordinario sube al **90 %** (factor `1.90`, o `1.80` según el tramo de transición configurado en tu perfil).\n"
                    + "• Sobre tu valor de hora ordinaria actual (**$7.959 COP**), un turno dominical ordinario de **8 horas** representa aproximadamente **$114.610 a $120.977 COP brutos** antes de seguridad social (8 %).\n"
                    + "• Puedes pedirme el cálculo exacto diciendo: *\"Con salario de 2000000 calcula un turno dominical de 8 horas\"*.";
        }

        if (norm.contains("prima") || norm.contains("cesantias") || norm.contains("vacaciones") || norm.contains("prestaciones")) {
            return "**Prestaciones Sociales en Colombia sobre tu Nómina:**\n\n"
                    + "• **Prima de servicios:** 1 mes de salario por año (50 % en junio y 50 % en diciembre), incluyendo el auxilio de transporte y el promedio de recargos/horas extras.\n"
                    + "• **Cesantías:** 1 mes de salario por cada año trabajado (8,33 % mensual sobre el devengado total + auxilio de transporte).\n"
                    + "• **Intereses sobre cesantías:** 12 % anual sobre el saldo acumulado de cesantías.\n"
                    + "• **Vacaciones:** 15 días hábiles remunerados por año (4,17 %, calculados sobre el salario ordinario y recargos sin incluir horas extras ni auxilio de transporte).";
        }

        return "**Respuesta del Asistente de Mi Nómina:**\n\n"
                + "Sobre tu consulta (*\"" + msg + "\"*), ten en cuenta estos datos actuales de tu perfil:\n\n"
                + contexto
                + "\n\n💡 *También puedes pedirme que ejecute una herramienta `@Tool` de cálculo escribiendo por ejemplo: \"Calcula el recargo de un turno dominical de 8 horas\" o \"Verifica el tope semanal para 46 horas\".*";
    }

    /**
     * 429 = cuota/rate limit agotado, 403 = a veces también es cuota/permiso
     * de ESA clave específica, 500/503 = error transitorio del lado de
     * Google. En estos casos vale la pena probar con otra clave.
     * 400/404 (modelo mal escrito, request mal formado) NO se arreglan
     * cambiando de clave, así que ahí no tiene sentido rotar.
     *
     * Recibe HttpStatusCode (la interfaz), no HttpStatus (el enum): desde
     * Spring Framework 6, HttpStatusCodeException.getStatusCode() devuelve
     * HttpStatusCode, y comparar por número de código evita el error de
     * compilación "incompatible types".
     */
    private boolean esErrorReintentableConOtraClave(HttpStatusCode status) {
        int codigo = status.value();
        return codigo == 429 // Too Many Requests
                || codigo == 403 // Forbidden
                || codigo == 500 // Internal Server Error
                || codigo == 503; // Service Unavailable
    }

    @SuppressWarnings("unchecked")
    private String extraerTexto(Map<String, Object> response) {
        if (response == null) return "No pude obtener una respuesta. Intenta de nuevo.";
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            StringBuilder texto = new StringBuilder();
            for (Map<String, Object> part : parts) {
                Object t = part.get("text");
                if (t != null) texto.append(t);
            }
            return texto.length() > 0 ? texto.toString() : "No pude obtener una respuesta. Intenta de nuevo.";
        } catch (Exception e) {
            log.error("Respuesta de Gemini con forma inesperada: {}", response, e);
            return "No pude obtener una respuesta. Intenta de nuevo.";
        }
    }

    private String resumirError(String cuerpoJson) {
        if (cuerpoJson == null || cuerpoJson.isBlank()) return "(sin detalle)";
        return cuerpoJson.length() > 300 ? cuerpoJson.substring(0, 300) + "..." : cuerpoJson;
    }
}
