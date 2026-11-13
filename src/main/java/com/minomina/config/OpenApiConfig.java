package com.minomina.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI miNominaOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Mi Nómina — API REST Interdisciplinaria")
                        .version("1.0.0")
                        .description("API RESTful para liquidación de nómina quincenal por turnos en Colombia (Ley 2101 de 2021 y Ley 2466 de 2025), asistente inteligente con Google Gemini, Data Warehouse en Esquema Estrella para Power BI y optimización matemática de turnos (PLE — Branch & Bound).")
                        .contact(new Contact()
                                .name("Andrés Camilo Troconis, Luis Castillo, Alan Cassiani")
                                .url("https://github.com/Troconisandres1/Mi-nomina")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
