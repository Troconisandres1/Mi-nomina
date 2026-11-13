package com.minomina.controller;

import com.minomina.model.Registro;
import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.OptimizacionTurnosService;
import com.minomina.service.OptimizacionTurnosService.CatalogoTurno;
import com.minomina.service.OptimizacionTurnosService.ParametrosOptimizacion;
import com.minomina.service.OptimizacionTurnosService.ResultadoOptimizacion;
import com.minomina.service.RegistroService;
import com.minomina.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/optimizacion")
public class OptimizacionController {

    private final OptimizacionTurnosService optimizacionService;
    private final UsuarioService usuarioService;
    private final RegistroService registroService;

    public OptimizacionController(OptimizacionTurnosService optimizacionService,
                                  UsuarioService usuarioService,
                                  RegistroService registroService) {
        this.optimizacionService = optimizacionService;
        this.usuarioService = usuarioService;
        this.registroService = registroService;
    }

    @GetMapping("/catalogo")
    public List<CatalogoTurno> catalogo() {
        return OptimizacionTurnosService.CATALOGO_TURNOS;
    }

    @PostMapping("/turnos")
    public ResultadoOptimizacion optimizar(@AuthenticationPrincipal AuthenticatedUser auth,
                                           @RequestBody(required = false) ParametrosOptimizacion req) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        List<Registro> registros = registroService.listar(usuario);
        ParametrosOptimizacion params = (req != null)
                ? req
                : ParametrosOptimizacion.porDefecto(LocalDate.of(2026, 11, 1));
        return optimizacionService.optimizarQuincena(usuario.getConfig(), params, registros);
    }
}
