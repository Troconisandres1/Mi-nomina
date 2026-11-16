package com.minomina.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

/**
 * Catálogo de días festivos nacionales en Colombia.
 */
@Service
public class FestivosService {

    private static final Set<LocalDate> FESTIVOS_2026 = Set.of(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 12),
            LocalDate.of(2026, 3, 23),
            LocalDate.of(2026, 4, 2),
            LocalDate.of(2026, 4, 3),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 6, 29),
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 17),
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 16),
            LocalDate.of(2026, 12, 8),
            LocalDate.of(2026, 12, 25)
    );

    public boolean esFestivo(LocalDate fecha) {
        return FESTIVOS_2026.contains(fecha);
    }

    public Set<LocalDate> listar() {
        return FESTIVOS_2026;
    }
}
