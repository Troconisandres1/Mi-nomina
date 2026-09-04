package com.minomina.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class ConfigNomina {

    private double hora = 7959;             // valor hora ordinaria
    private double rn = 35;                 // % recargo nocturno
    private double dom = 80;                // % dominical/festivo
    private double hed = 25;                // % hora extra diurna
    private double hen = 75;                // % hora extra nocturna
    private double hedd = 105;              // % extra dominical diurna
    private double hend = 155;              // % extra dominical nocturna
    private double auxTranspMensual = 249095;

    public static ConfigNomina porDefecto() {
        return new ConfigNomina();
    }

    // getters y setters
    public double getHora() { return hora; }
    public void setHora(double hora) { this.hora = hora; }

    public double getRn() { return rn; }
    public void setRn(double rn) { this.rn = rn; }

    public double getDom() { return dom; }
    public void setDom(double dom) { this.dom = dom; }

    public double getHed() { return hed; }
    public void setHed(double hed) { this.hed = hed; }

    public double getHen() { return hen; }
    public void setHen(double hen) { this.hen = hen; }

    public double getHedd() { return hedd; }
    public void setHedd(double hedd) { this.hedd = hedd; }

    public double getHend() { return hend; }
    public void setHend(double hend) { this.hend = hend; }

    public double getAuxTranspMensual() { return auxTranspMensual; }
    public void setAuxTranspMensual(double auxTranspMensual) { this.auxTranspMensual = auxTranspMensual; }
}
