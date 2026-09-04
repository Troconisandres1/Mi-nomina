package com.minomina.service;

import com.minomina.model.ConfigNomina;
import com.minomina.model.Historial;
import com.minomina.model.Registro;
import com.minomina.model.Usuario;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Service
public class AiContextBuilder {

    private final ResumenService resumenService;
    private final NumberFormat cop = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public AiContextBuilder(ResumenService resumenService) {
        this.resumenService = resumenService;
    }

    public String systemPrompt(Usuario usuario) {
        return """
            Eres un asistente experto en nómina laboral colombiana, integrado en la app "Mi Nómina" de %s.
            Tienes acceso a su configuración salarial, a cada registro de la quincena actual (con desglose de horas)
            y al historial de quincenas cerradas.

            REGLAS:
            - Responde siempre en español, tono cercano pero profesional.
            - Usa SIEMPRE los números exactos del contexto entregado. Nunca inventes cifras.
            - Si algo no está en los datos, dilo claramente.
            - Formato colombiano para valores: $1.234.567
            - No uses tablas markdown, solo texto con guiones.
            - No cortes una respuesta a la mitad; cierra bien cada idea.
            """.formatted(usuario.getNombre());
    }

    public String contextoNomina(Usuario usuario, List<Registro> registrosActuales, List<Historial> historial) {
        ConfigNomina cfg = usuario.getConfig();
        StringBuilder sb = new StringBuilder();

        sb.append("CONFIGURACIÓN LABORAL:\n");
        sb.append("- Valor hora ordinaria: ").append(cop.format(cfg.getHora())).append("\n");
        sb.append("- Recargo nocturno: ").append(cfg.getRn()).append("% | Dominical/Festivo: ").append(cfg.getDom()).append("%\n");
        sb.append("- Extra diurna: ").append(cfg.getHed()).append("% | Extra nocturna: ").append(cfg.getHen()).append("%\n");
        sb.append("- Auxilio transporte mensual: ").append(cop.format(cfg.getAuxTranspMensual())).append("\n");
        sb.append("- Deducciones: Salud 4% + Pensión 4% = 8% sobre IBC\n\n");

        if (registrosActuales.isEmpty()) {
            sb.append("QUINCENA ACTUAL: Sin registros aún.\n\n");
        } else {
            var resumen = resumenService.calcular(registrosActuales, cfg);
            sb.append("QUINCENA ACTUAL — ").append(registrosActuales.size()).append(" días registrados\n");
            for (Registro r : registrosActuales) {
                sb.append("  [").append(r.getFecha()).append("] ").append(r.getTipo());
                if (r.getDiagnostico() != null) sb.append(" — ").append(r.getDiagnostico());
                sb.append(" | Pago: ").append(cop.format(r.getPagoSalarial())).append("\n");
            }
            sb.append("\nTOTALES QUINCENA ACTUAL:\n");
            sb.append("- IBC bruto acumulado: ").append(cop.format(resumen.ibc())).append("\n");
            sb.append("- Auxilio de transporte: ").append(cop.format(resumen.transporte())).append("\n");
            sb.append("- Deducciones (8%): -").append(cop.format(resumen.deduccionTotal())).append("\n");
            sb.append("- NETO ACTUAL: ").append(cop.format(resumen.neto())).append("\n\n");
        }

        if (historial.isEmpty()) {
            sb.append("HISTORIAL: Sin quincenas cerradas aún.\n");
        } else {
            sb.append("HISTORIAL DE QUINCENAS ANTERIORES — ").append(historial.size()).append(" quincenas:\n");
            for (Historial h : historial) {
                sb.append("  Período: ").append(h.getDesde()).append(" al ").append(h.getHasta())
                        .append(" | NETO: ").append(cop.format(h.getNeto())).append("\n");
            }
        }

        return sb.toString();
    }
}
