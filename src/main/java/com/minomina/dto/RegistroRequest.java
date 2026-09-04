package com.minomina.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Espejo del formulario "Registrar Día" del frontend (#formTurno).
 * tipo: NORMAL | COMPENSATORIO | INCAPACIDAD | NO_LABORADO
 */
public class RegistroRequest {

    @NotNull
    private LocalDate fecha;

    @NotBlank
    private String tipo;

    // Solo aplica si tipo == NORMAL
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private LocalTime descansoInicio;
    private LocalTime descansoFin;

    // Solo aplica si tipo == INCAPACIDAD
    private String diagnostico;
    private Integer numDiasIncapacidad; // 1 a 4

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public LocalTime getDescansoInicio() { return descansoInicio; }
    public void setDescansoInicio(LocalTime descansoInicio) { this.descansoInicio = descansoInicio; }

    public LocalTime getDescansoFin() { return descansoFin; }
    public void setDescansoFin(LocalTime descansoFin) { this.descansoFin = descansoFin; }

    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }

    public Integer getNumDiasIncapacidad() { return numDiasIncapacidad; }
    public void setNumDiasIncapacidad(Integer numDiasIncapacidad) { this.numDiasIncapacidad = numDiasIncapacidad; }
}
