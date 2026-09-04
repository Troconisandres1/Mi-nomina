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
            throw new IllegalStateException("No hay ninguna GEMINI_API_KEY configurada en el servidor");
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

        // Se agotaron TODAS las claves configuradas.
        log.error("Las {} clave(s) de Gemini configuradas fallaron. Último error: {}", total, ultimoError);
        throw new IllegalStateException(
                "Todas las claves de Gemini configuradas están agotadas o sin permisos. Último error: " + ultimoError);
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
