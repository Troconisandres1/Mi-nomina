package com.minomina.service;

import com.minomina.dto.RegistroRequest;
import com.minomina.model.Registro;
import com.minomina.model.TipoDia;
import com.minomina.model.Usuario;
import com.minomina.repository.RegistroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegistroService {

    // Valor fijo del día de compensatorio remunerado (igual al frontend original).
    private static final double VALOR_COMPENSATORIO = 58364;
    private static final double HORAS_COMPENSATORIO = 7.55;
    private static final double FACTOR_SALARIO_DIARIO_INCAPACIDAD = 7.55;

    private final RegistroRepository registroRepository;
    private final PayrollEngine payrollEngine;

    public RegistroService(RegistroRepository registroRepository, PayrollEngine payrollEngine) {
        this.registroRepository = registroRepository;
        this.payrollEngine = payrollEngine;
    }

    public List<Registro> listar(Usuario usuario) {
        return registroRepository.findByUsuarioOrderByFechaAsc(usuario);
    }

    @Transactional
    public List<Registro> guardar(Usuario usuario, RegistroRequest req) {
        TipoDia tipo = TipoDia.valueOf(req.getTipo());

        switch (tipo) {
            case NORMAL -> {
                if (req.getHoraInicio() == null || req.getHoraFin() == null) {
                    throw new IllegalArgumentException("Ingresa hora de entrada y salida");
                }
                // Se elimina el registro existente de ese día ANTES de calcular el acumulado
                // semanal, para que no se cuente a sí mismo (igual que en el JS original).
                registroRepository.findByUsuarioAndFecha(usuario, req.getFecha())
                        .ifPresent(registroRepository::delete);
                registroRepository.flush();

                List<Registro> previos = registroRepository.findByUsuarioOrderByFechaAsc(usuario);

                var resultado = payrollEngine.calcularTurno(
                        usuario.getConfig(), req.getFecha(),
                        req.getHoraInicio(), req.getHoraFin(),
                        req.getDescansoInicio(), req.getDescansoFin(),
                        previos
                );

                Registro r = new Registro();
                r.setUsuario(usuario);
                r.setFecha(req.getFecha());
                r.setTipo(TipoDia.NORMAL);
                r.setPagaTransporte(true);
                r.setHoraInicio(req.getHoraInicio());
                r.setHoraFin(req.getHoraFin());
                r.setDescansoInicio(req.getDescansoInicio());
                r.setDescansoFin(req.getDescansoFin());
                r.setDesglose(resultado.desglose());
                r.setPagoSalarial(resultado.pagoSalarial());
                registroRepository.save(r);
            }

            case INCAPACIDAD -> {
                int numDias = req.getNumDiasIncapacidad() == null ? 1
                        : Math.min(4, Math.max(1, req.getNumDiasIncapacidad()));
                String diag = (req.getDiagnostico() == null || req.getDiagnostico().isBlank())
                        ? "Incapacidad General" : req.getDiagnostico();
                double salarioDiario = usuario.getConfig().getHora() * FACTOR_SALARIO_DIARIO_INCAPACIDAD;

                for (int i = 0; i < numDias; i++) {
                    LocalDate f = req.getFecha().plusDays(i);
                    registroRepository.findByUsuarioAndFecha(usuario, f).ifPresent(registroRepository::delete);
                }
                registroRepository.flush();

                for (int i = 0; i < numDias; i++) {
                    LocalDate f = req.getFecha().plusDays(i);
                    Registro r = new Registro();
                    r.setUsuario(usuario);
                    r.setFecha(f);
                    r.setTipo(TipoDia.INCAPACIDAD);
                    r.setPagaTransporte(false);
                    r.setDiagnostico(diag);
                    r.setPagoSalarial(salarioDiario);

                    Map<String, Object> desglose = new LinkedHashMap<>();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("h", 0);
                    item.put("v", salarioDiario);
                    item.put("pct", "100%");
                    desglose.put("INCAP", item);
                    r.setDesglose(desglose);

                    registroRepository.save(r);
                }
            }

            case COMPENSATORIO -> {
                registroRepository.findByUsuarioAndFecha(usuario, req.getFecha())
                        .ifPresent(registroRepository::delete);
                registroRepository.flush();

                Registro r = new Registro();
                r.setUsuario(usuario);
                r.setFecha(req.getFecha());
                r.setTipo(TipoDia.COMPENSATORIO);
                r.setPagaTransporte(true);
                r.setPagoSalarial(VALOR_COMPENSATORIO);

                Map<String, Object> desglose = new LinkedHashMap<>();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("h", HORAS_COMPENSATORIO);
                item.put("v", VALOR_COMPENSATORIO);
                desglose.put("COMPENSATORIO", item);
                r.setDesglose(desglose);

                registroRepository.save(r);
            }

            case NO_LABORADO -> {
                registroRepository.findByUsuarioAndFecha(usuario, req.getFecha())
                        .ifPresent(registroRepository::delete);
                registroRepository.flush();

                Registro r = new Registro();
                r.setUsuario(usuario);
                r.setFecha(req.getFecha());
                r.setTipo(TipoDia.NO_LABORADO);
                r.setPagaTransporte(false);
                r.setPagoSalarial(0);
                r.setDesglose(new LinkedHashMap<>());
                registroRepository.save(r);
            }
        }

        return listar(usuario);
    }

    @Transactional
    public void eliminar(Usuario usuario, LocalDate fecha) {
        registroRepository.deleteByUsuarioAndFecha(usuario, fecha);
    }

    @Transactional
    public void eliminarTodos(Usuario usuario) {
        registroRepository.deleteAllByUsuario(usuario);
    }

    /**
     * Recalcula TODOS los turnos NORMAL del usuario en orden cronológico con el
     * motor actual. Equivalente al botón "Recalcular quincena actual" del frontend.
     */
    @Transactional
    public List<Registro> recalcularTodo(Usuario usuario) {
        List<Registro> registros = registroRepository.findByUsuarioOrderByFechaAsc(usuario);

        for (Registro r : registros) {
            if (r.getTipo() != TipoDia.NORMAL || r.getHoraInicio() == null || r.getHoraFin() == null) continue;

            List<Registro> previos = registros.stream()
                    .filter(x -> !x.getId().equals(r.getId()))
                    .toList();

            var resultado = payrollEngine.calcularTurno(
                    usuario.getConfig(), r.getFecha(),
                    r.getHoraInicio(), r.getHoraFin(),
                    r.getDescansoInicio(), r.getDescansoFin(),
                    previos
            );
            r.setDesglose(resultado.desglose());
            r.setPagoSalarial(resultado.pagoSalarial());
            registroRepository.save(r);
        }
        return listar(usuario);
    }
}
