package com.minomina.controller;

import com.minomina.model.Usuario;
import com.minomina.service.UsuarioService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UsuarioService usuarioService;

    public AdminController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/usuarios/pendientes")
    public List<Map<String, String>> pendientes() {
        return usuarioService.listarPendientes().stream()
                .map(u -> Map.of("id", u.getId(), "email", u.getEmail(), "nombre", u.getNombre()))
                .collect(Collectors.toList());
    }

    @PostMapping("/usuarios/{id}/aprobar")
    public Map<String, String> aprobar(@PathVariable String id) {
        Usuario u = usuarioService.aprobar(id);
        return Map.of("id", u.getId(), "estado", u.getEstado().name());
    }

    @PostMapping("/usuarios/{id}/rechazar")
    public Map<String, String> rechazar(@PathVariable String id) {
        Usuario u = usuarioService.rechazar(id);
        return Map.of("id", u.getId(), "estado", u.getEstado().name());
    }
}
