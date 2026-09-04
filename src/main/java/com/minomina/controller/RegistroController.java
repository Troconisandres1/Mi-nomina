package com.minomina.controller;

import com.minomina.dto.RegistroRequest;
import com.minomina.dto.RegistroResponse;
import com.minomina.model.Registro;
import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.RegistroService;
import com.minomina.service.ResumenService;
import com.minomina.service.ResumenService.Resumen;
import com.minomina.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/registros")
public class RegistroController {

    private final RegistroService registroService;
    private final UsuarioService usuarioService;
    private final ResumenService resumenService;

    public RegistroController(RegistroService registroService, UsuarioService usuarioService,
                               ResumenService resumenService) {
        this.registroService = registroService;
        this.usuarioService = usuarioService;
        this.resumenService = resumenService;
    }

    @GetMapping
    public List<RegistroResponse> listar(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        return registroService.listar(usuario).stream().map(RegistroResponse::desde).toList();
    }

    @PostMapping
    public List<RegistroResponse> guardar(@AuthenticationPrincipal AuthenticatedUser auth,
                                           @Valid @RequestBody RegistroRequest req) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        List<Registro> registros = registroService.guardar(usuario, req);
        return registros.stream().map(RegistroResponse::desde).toList();
    }

    @DeleteMapping("/{fecha}")
    public void eliminar(@AuthenticationPrincipal AuthenticatedUser auth, @PathVariable LocalDate fecha) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        registroService.eliminar(usuario, fecha);
    }

    @GetMapping("/resumen")
    public Resumen resumen(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        List<Registro> registros = registroService.listar(usuario);
        return resumenService.calcular(registros, usuario.getConfig());
    }

    @PostMapping("/recalcular")
    public List<RegistroResponse> recalcular(@AuthenticationPrincipal AuthenticatedUser auth) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
        return registroService.recalcularTodo(usuario).stream().map(RegistroResponse::desde).toList();
    }
}
