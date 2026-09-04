package com.minomina.service;

import com.minomina.model.ConfigNomina;
import com.minomina.model.Registro;
import com.minomina.model.TipoDia;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResumenService {

    public record Resumen(
            double ibc,
            double transporte,
            double deduccionSalud,
            double deduccionPension,
            double deduccionTotal,
            double neto,
            int diasRegistrados,
            int diasPaganTransporte
    ) { }

    public Resumen calcular(List<Registro> registros, ConfigNomina cfg) {
        double ibc = 0;
        double baseDeducible = 0;
        int diasT = 0;

        for (Registro r : registros) {
            ibc += r.getPagoSalarial();
            if (r.getTipo() != TipoDia.INCAPACIDAD) baseDeducible += r.getPagoSalarial();
            if (r.isPagaTransporte()) diasT++;
        }

        double transporte = (cfg.getAuxTranspMensual() / 30.0) * diasT;
        double salud = baseDeducible * 0.04;
        double pension = baseDeducible * 0.04;
        double deduccionTotal = salud + pension;
        double neto = ibc + transporte - deduccionTotal;

        return new Resumen(ibc, transporte, salud, pension, deduccionTotal, neto, registros.size(), diasT);
    }
}
