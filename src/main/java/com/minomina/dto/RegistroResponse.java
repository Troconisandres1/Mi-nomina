package com.minomina.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public class RegistroResponse {
    private String id;
    private LocalDate fecha;
    private String tipo;
    private double pagoSalarial;
    private boolean pagaTransporte;
    private String diagnostico;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private LocalTime descansoInicio;
    private LocalTime descansoFin;
    private Map<String, Object> desglose;

    public RegistroResponse() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public double getPagoSalarial() { return pagoSalarial; }
    public void setPagoSalarial(double pagoSalarial) { this.pagoSalarial = pagoSalarial; }

    public boolean isPagaTransporte() { return pagaTransporte; }
    public void setPagaTransporte(boolean pagaTransporte) { this.pagaTransporte = pagaTransporte; }

    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public LocalTime getDescansoInicio() { return descansoInicio; }
    public void setDescansoInicio(LocalTime descansoInicio) { this.descansoInicio = descansoInicio; }

    public LocalTime getDescansoFin() { return descansoFin; }
    public void setDescansoFin(LocalTime descansoFin) { this.descansoFin = descansoFin; }

    public Map<String, Object> getDesglose() { return desglose; }
    public void setDesglose(Map<String, Object> desglose) { this.desglose = desglose; }

    public static RegistroResponse desde(com.minomina.model.Registro r) {
        RegistroResponse dto = new RegistroResponse();
        dto.setId(r.getId());
        dto.setFecha(r.getFecha());
        dto.setTipo(r.getTipo().name());
        dto.setPagoSalarial(r.getPagoSalarial());
        dto.setPagaTransporte(r.isPagaTransporte());
        dto.setDiagnostico(r.getDiagnostico());
        dto.setHoraInicio(r.getHoraInicio());
        dto.setHoraFin(r.getHoraFin());
        dto.setDescansoInicio(r.getDescansoInicio());
        dto.setDescansoFin(r.getDescansoFin());
        dto.setDesglose(r.getDesglose());
        return dto;
    }
}
