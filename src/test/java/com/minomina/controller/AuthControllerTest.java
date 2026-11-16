package com.minomina.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registroConDatosInvalidosDevuelve400ConDetallePorCampo() throws Exception {
        String cuerpoInvalido = """
                {"nombre":"","email":"esto-no-es-un-correo","password":"123"}
                """;

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.nombre").exists())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    void registroConDatosValidosDevuelveMensajeDeConfirmacion() throws Exception {
        String cuerpoValido = """
                {"nombre":"Usuario de Prueba","email":"junit-test@example.com","password":"123456"}
                """;

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void loginConCredencialesIncorrectasDevuelve401() throws Exception {
        String credencialesFalsas = """
                {"email":"nadie@example.com","password":"loquesea"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credencialesFalsas))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }
}
