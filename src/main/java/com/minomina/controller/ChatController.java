package com.minomina.controller;

import com.minomina.tools.HerramientasAcademicas;
import com.minomina.tools.HerramientasNomina;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para el taller práctico de Tool Calling con Spring AI.
 * Expone el endpoint POST /api/chat que registra tanto las herramientas académicas
 * (HerramientasAcademicas) como las herramientas de liquidación laboral de Mi Nómina
 * (HerramientasNomina) mediante .tools(...).
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final HerramientasAcademicas herramientas;
    private final HerramientasNomina herramientasNomina;

    public ChatController(
            ChatClient.Builder builder,
            HerramientasAcademicas herramientas,
            HerramientasNomina herramientasNomina) {

        this.chatClient = builder.build();
        this.herramientas = herramientas;
        this.herramientasNomina = herramientasNomina;
    }

    @PostMapping
    public String chat(@RequestBody String mensaje) {

        return chatClient
                .prompt()
                .user(mensaje)
                .tools(herramientas, herramientasNomina)
                .call()
                .content();
    }
}
