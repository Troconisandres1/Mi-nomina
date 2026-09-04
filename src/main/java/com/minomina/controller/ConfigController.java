package com.minomina.controller;

import com.minomina.model.ConfigNomina;
import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final UsuarioService usuarioService;

    public ConfigController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ConfigNomina obtener(@AuthenticationPrincipal AuthenticatedUser auth) {
        return usuarioService.obtenerPorId(auth.getUserId()).getConfig();
    }

    @PutMapping
    public ConfigNomina actualizar(@AuthenticationPrincipal AuthenticatedUser auth, @RequestBody ConfigNomina nuevaConfig) {
        Usuario u = usuarioService.guardarConfig(auth.getUserId(), nuevaConfig);
        return u.getConfig();
    }
}
