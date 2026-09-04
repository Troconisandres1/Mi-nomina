package com.minomina.service;

import com.minomina.dto.RegistroResponse;
import com.minomina.model.Historial;
import com.minomina.model.Registro;
import com.minomina.model.Usuario;
import com.minomina.repository.HistorialRepository;
import com.minomina.repository.RegistroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistorialService {

    private final HistorialRepository historialRepository;
    private final RegistroRepository registroRepository;
    private final ResumenService resumenService;

    public HistorialService(HistorialRepository historialRepository, RegistroRepository registroRepository,
                             ResumenService resumenService) {
        this.historialRepository = historialRepository;
        this.registroRepository = registroRepository;
        this.resumenService = resumenService;
    }

    public List<Historial> listar(Usuario usuario) {
        return historialRepository.findByUsuarioOrderByDesdeDesc(usuario);
    }

    /** Equivalente a clearQuincena(): guarda el estado actual en historial y limpia los registros. */
    @Transactional
    public Historial cerrarQuincena(Usuario usuario) {
        List<Registro> registros = registroRepository.findByUsuarioOrderByFechaAsc(usuario);
        if (registros.isEmpty()) {
            throw new IllegalStateException("No hay registros para cerrar");
        }

        var resumen = resumenService.calcular(registros, usuario.getConfig());

        Historial h = new Historial();
        h.setUsuario(usuario);
        h.setDesde(registros.get(0).getFecha());
        h.setHasta(registros.get(registros.size() - 1).getFecha());
        h.setNeto(resumen.neto());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<Map<String, Object>> registrosSerializados = registros.stream()
                .map(this::serializar)
                .toList();
        snapshot.put("registros", registrosSerializados);
        h.setSnapshot(snapshot);

        historialRepository.save(h);
        registroRepository.deleteAllByUsuario(usuario);

        return h;
    }

    @Transactional
    public void eliminar(Usuario usuario, String historialId) {
        Historial h = historialRepository.findById(historialId)
                .orElseThrow(() -> new IllegalArgumentException("Historial no encontrado"));
        if (!h.getUsuario().getId().equals(usuario.getId())) {
            throw new SecurityException("No autorizado");
        }
        historialRepository.delete(h);
    }

    private Map<String, Object> serializar(Registro r) {
        RegistroResponse dto = RegistroResponse.desde(r);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fecha", dto.getFecha().toString());
        map.put("tipo", dto.getTipo());
        map.put("pagoSalarial", dto.getPagoSalarial());
        map.put("pagaTransporte", dto.isPagaTransporte());
        map.put("diagnostico", dto.getDiagnostico());
        map.put("desglose", dto.getDesglose());
        if (dto.getHoraInicio() != null) {
            map.put("horaInicio", dto.getHoraInicio().toString());
            map.put("horaFin", dto.getHoraFin().toString());
            if (dto.getDescansoInicio() != null) map.put("descansoInicio", dto.getDescansoInicio().toString());
            if (dto.getDescansoFin() != null) map.put("descansoFin", dto.getDescansoFin().toString());
        }
        return map;
    }
}
