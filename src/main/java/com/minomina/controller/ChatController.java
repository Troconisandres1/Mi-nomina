package com.minomina.controller;

import com.minomina.tools.HerramientasNomina;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para la integración de Spring AI Tool Calling en Mi Nómina.
 * Expone el endpoint POST /api/chat que registra las herramientas de liquidación
 * y validación laboral (HerramientasNomina) mediante .tools(herramientas).
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final HerramientasNomina herramientas;

    public ChatController(
            ChatClient.Builder builder,
            HerramientasNomina herramientas) {

        this.chatClient = builder.build();
        this.herramientas = herramientas;
    }

    @PostMapping
    public String chat(@RequestBody String mensaje) {

        return chatClient
                .prompt()
                .user(mensaje)
                .tools(herramientas)
                .call()
                .content();
    }
}
