package com.minomina.controller;

import com.minomina.dto.HistorialResponse;
import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.HistorialService;
import com.minomina.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historial")
@Tag(name = "Historial de Quincenas", description = "Cierre y consulta de cortes quincenales históricos del trabajador")
public class HistorialController {

    private final HistorialService historialService;
    private final UsuarioService usuarioService;

    public HistorialController(HistorialService historialService, UsuarioService usuarioService) {
        this.historialService = historialService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "Listar quincenas cerradas", description = "Devuelve los cortes quincenales cerrados por el usuario autenticado mediante DTO.")
    public List<HistorialResponse> listar(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        return historialService.listar(usuario).stream().map(HistorialResponse::desde).toList();
    }

    @PostMapping("/cerrar-quincena")
    @Operation(summary = "Cerrar quincena activa", description = "Consolida los turnos actuales en una instantánea histórica y limpia la quincena en curso.")
    public HistorialResponse cerrarQuincena(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        return HistorialResponse.desde(historialService.cerrarQuincena(usuario));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar quincena histórica", description = "Elimina un registro histórico perteneciente al usuario autenticado.")
    public void eliminar(@AuthenticationPrincipal AuthenticatedUser auth, @PathVariable String id) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        historialService.eliminar(usuario, id);
    }
}
