package com.minomina.dto;

import com.minomina.model.Historial;

import java.time.LocalDate;
import java.util.Map;

public record HistorialResponse(
        String id,
        LocalDate desde,
        LocalDate hasta,
        double neto,
        Map<String, Object> snapshot
) {
    public static HistorialResponse desde(Historial h) {
        return new HistorialResponse(
                h.getId(),
                h.getDesde(),
                h.getHasta(),
                h.getNeto(),
                h.getSnapshot()
        );
    }
}
