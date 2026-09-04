package com.minomina.controller;

import com.minomina.dto.AuthDtos.*;
import com.minomina.model.Usuario;
import com.minomina.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/registro")
    public ResponseEntity<MensajeResponse> registrar(@Valid @RequestBody RegisterRequest req) {
        Usuario u = usuarioService.registrar(req);
        String msg = u.getEstado().name().equals("APROBADO")
                ? "Cuenta creada y aprobada automáticamente. Ya puedes iniciar sesión."
                : "Solicitud enviada. Tu cuenta queda pendiente de aprobación por el administrador.";
        return ResponseEntity.ok(new MensajeResponse(msg));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(usuarioService.login(req));
    }
}
