package com.minomina.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check público del servicio.
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
