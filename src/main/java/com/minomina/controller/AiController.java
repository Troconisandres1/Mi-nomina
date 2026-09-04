package com.minomina.controller;

import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiProxyService aiProxyService;
    private final AiContextBuilder aiContextBuilder;
    private final UsuarioService usuarioService;
    private final RegistroService registroService;
    private final HistorialService historialService;

    public AiController(AiProxyService aiProxyService, AiContextBuilder aiContextBuilder,
                         UsuarioService usuarioService, RegistroService registroService,
                         HistorialService historialService) {
        this.aiProxyService = aiProxyService;
        this.aiContextBuilder = aiContextBuilder;
        this.usuarioService = usuarioService;
        this.registroService = registroService;
        this.historialService = historialService;
    }

    public record ChatRequest(String mensaje, List<String> historialReciente) { }
    public record ChatResponse(String respuesta) { }

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal AuthenticatedUser auth, @RequestBody ChatRequest req) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());

        String contexto = aiContextBuilder.contextoNomina(
                usuario, registroService.listar(usuario), historialService.listar(usuario));
        String systemPrompt = aiContextBuilder.systemPrompt(usuario);
        String historialTexto = req.historialReciente() == null ? "" : String.join("\n\n", req.historialReciente());

        String respuesta = aiProxyService.preguntar(systemPrompt, contexto, historialTexto, req.mensaje());
        return new ChatResponse(respuesta);
    }
}
