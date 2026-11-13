package com.minomina.controller;

import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.*;
import com.minomina.tools.HerramientasNomina;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiProxyService aiProxyService;
    private final AiContextBuilder aiContextBuilder;
    private final UsuarioService usuarioService;
    private final RegistroService registroService;
    private final HistorialService historialService;
    private final ChatClient chatClient;
    private final HerramientasNomina herramientasNomina;

    public AiController(AiProxyService aiProxyService, AiContextBuilder aiContextBuilder,
                         UsuarioService usuarioService, RegistroService registroService,
                         HistorialService historialService,
                         ChatClient.Builder chatClientBuilder,
                         HerramientasNomina herramientasNomina) {
        this.aiProxyService = aiProxyService;
        this.aiContextBuilder = aiContextBuilder;
        this.usuarioService = usuarioService;
        this.registroService = registroService;
        this.historialService = historialService;
        this.chatClient = chatClientBuilder.build();
        this.herramientasNomina = herramientasNomina;
    }

    public record ChatRequest(String mensaje, List<String> historialReciente) { }
    public record ChatResponse(String respuesta) { }

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal AuthenticatedUser auth, @RequestBody ChatRequest req) {
        Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());

        // Si el mensaje solicita un cálculo o verificación determinista de nómina cubierto por @Tool,
        // se ejecuta la herramienta registrada en HerramientasNomina:
        String toolCheck = chatClient.prompt()
                .user(req.mensaje())
                .tools(herramientasNomina)
                .call()
                .content();
        if (toolCheck != null && !toolCheck.contains("sin invocar ninguna herramienta")
                && !toolCheck.contains("sin necesidad de ejecutar herramientas")) {
            return new ChatResponse(toolCheck);
        }

        String contexto = aiContextBuilder.contextoNomina(
                usuario, registroService.listar(usuario), historialService.listar(usuario));
        String systemPrompt = aiContextBuilder.systemPrompt(usuario);
        String historialTexto = req.historialReciente() == null ? "" : String.join("\n\n", req.historialReciente());

        String respuesta = aiProxyService.preguntar(systemPrompt, contexto, historialTexto, req.mensaje());
        return new ChatResponse(respuesta);
    }
}
