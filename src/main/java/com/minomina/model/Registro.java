package com.minomina.model;

import com.minomina.util.JsonMapConverter;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "registros", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "fecha"}))
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDia tipo;

    @Column(nullable = false)
    private double pagoSalarial = 0;

    @Column(nullable = false)
    private boolean pagaTransporte = false;

    private String diagnostico;

    // Horario crudo del turno (solo aplica a tipo NORMAL)
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private LocalTime descansoInicio;
    private LocalTime descansoFin;

    // Desglose de horas por concepto: { "ordD": {"h":..,"v":..}, "ordN": {...}, ... }
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> desglose = new LinkedHashMap<>();

    public Registro() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public TipoDia getTipo() { return tipo; }
    public void setTipo(TipoDia tipo) { this.tipo = tipo; }

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
}
