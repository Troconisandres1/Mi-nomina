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

/**
 * Prueba de la capa Controller con MockMvc, contra el contexto real de Spring
 * (misma app.yml de pruebas: H2 en memoria, ver src/test/resources).
 * Automatiza exactamente lo que ya se verificó manualmente con curl durante
 * el diagnóstico de manejo de errores: datos inválidos → 400 con detalle por
 * campo, y un registro válido → 200 con mensaje de confirmación.
 */
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
