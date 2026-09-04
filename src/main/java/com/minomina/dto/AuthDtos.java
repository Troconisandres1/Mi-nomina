package com.minomina.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RegisterRequest(
            @NotBlank String nombre,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password
    ) {}

    public record AuthResponse(
            String token,
            String userId,
            String email,
            String nombre,
            String estado,
            String rol
    ) {}

    public record MensajeResponse(String mensaje) {}
}
