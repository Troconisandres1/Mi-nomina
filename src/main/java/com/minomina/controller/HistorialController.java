package com.minomina.controller;

import com.minomina.model.Historial;
import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.HistorialService;
import com.minomina.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historial")
public class HistorialController {

    private final HistorialService historialService;
    private final UsuarioService usuarioService;

    public HistorialController(HistorialService historialService, UsuarioService usuarioService) {
        this.historialService = historialService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<Historial> listar(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        return historialService.listar(usuario);
    }

    @PostMapping("/cerrar-quincena")
    public Historial cerrarQuincena(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        return historialService.cerrarQuincena(usuario);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@AuthenticationPrincipal AuthenticatedUser auth, @PathVariable String id) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        historialService.eliminar(usuario, id);
    }
}
