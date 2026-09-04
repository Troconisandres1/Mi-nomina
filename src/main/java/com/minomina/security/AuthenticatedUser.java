package com.minomina.security;

/**
 * Representa el "principal" guardado en el SecurityContext tras validar el JWT.
 * Se usa en los controladores vía @AuthenticationPrincipal.
 */
public class AuthenticatedUser {

    private final String userId;
    private final String email;
    private final String rol;

    public AuthenticatedUser(String userId, String email, String rol) {
        this.userId = userId;
        this.email = email;
        this.rol = rol;
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRol() { return rol; }
}
