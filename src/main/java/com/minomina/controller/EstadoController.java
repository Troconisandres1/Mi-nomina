package com.minomina.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint de estado, público (no requiere token). Sirve para el flujo de
 * Pull Request del taller de CI/CD (Paso 5) y como comprobación rápida de
 * que el backend está arriba.
 */
@RestController
@RequestMapping("/api/estado")
public class EstadoController {

    @GetMapping
    public Map<String, Object> estado() {
        return Map.of(
                "estado", "ok",
                "servicio", "mi-nomina-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
