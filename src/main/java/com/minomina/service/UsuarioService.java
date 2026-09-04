package com.minomina.service;

import com.minomina.dto.AuthDtos.AuthResponse;
import com.minomina.dto.AuthDtos.LoginRequest;
import com.minomina.dto.AuthDtos.RegisterRequest;
import com.minomina.model.EstadoUsuario;
import com.minomina.model.RolUsuario;
import com.minomina.model.Usuario;
import com.minomina.repository.UsuarioRepository;
import com.minomina.security.JwtService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.admin.bootstrap-email}")
    private String adminBootstrapEmail;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostConstruct
    void avisarSiNoHayAdminConfigurado() {
        if (adminBootstrapEmail == null || adminBootstrapEmail.isBlank()) {
            log.warn("ADMIN_EMAIL no está configurado: nadie podrá auto-registrarse como administrador. " +
                    "Configura la variable de entorno ADMIN_EMAIL con tu correo real antes de registrarte como admin.");
        }
    }

    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException(String msg) { super(msg); }
    }

    public static class CuentaPendienteException extends RuntimeException {
        public CuentaPendienteException(String msg) { super(msg); }
    }

    public static class EmailYaRegistradoException extends RuntimeException {
        public EmailYaRegistradoException(String msg) { super(msg); }
    }

    public Usuario registrar(RegisterRequest req) {
        if (usuarioRepository.existsByEmail(req.email().toLowerCase())) {
            throw new EmailYaRegistradoException("Ese correo ya está registrado");
        }
        Usuario u = new Usuario(req.email().toLowerCase(), passwordEncoder.encode(req.password()), req.nombre());

        // Solo si ADMIN_EMAIL fue configurado explícitamente (nunca por defecto
        // en el código) y coincide exactamente, el registro queda como ADMIN
        // aprobado automáticamente. Si adminBootstrapEmail está vacío, esta
        // condición nunca es verdadera y CUALQUIER registro cae al else.
        boolean esElAdminConfigurado = adminBootstrapEmail != null
                && !adminBootstrapEmail.isBlank()
                && req.email().equalsIgnoreCase(adminBootstrapEmail);

        if (esElAdminConfigurado) {
            u.setEstado(EstadoUsuario.APROBADO);
            u.setRol(RolUsuario.ADMIN);
        } else {
            u.setEstado(EstadoUsuario.PENDIENTE);
            u.setRol(RolUsuario.USER);
        }
        return usuarioRepository.save(u);
    }

    public AuthResponse login(LoginRequest req) {
        Usuario u = usuarioRepository.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new CredencialesInvalidasException("Correo o contraseña incorrectos"));

        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }

        if (u.getEstado() != EstadoUsuario.APROBADO) {
            throw new CuentaPendienteException("Tu cuenta está pendiente de aprobación por el administrador");
        }

        String token = jwtService.generarToken(u.getId(), u.getEmail(), u.getRol().name());
        return new AuthResponse(token, u.getId(), u.getEmail(), u.getNombre(), u.getEstado().name(), u.getRol().name());
    }

    public Usuario obtenerPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    public List<Usuario> listarPendientes() {
        return usuarioRepository.findByEstado(EstadoUsuario.PENDIENTE);
    }

    public Usuario aprobar(String usuarioId) {
        Usuario u = obtenerPorId(usuarioId);
        u.setEstado(EstadoUsuario.APROBADO);
        return usuarioRepository.save(u);
    }

    public Usuario rechazar(String usuarioId) {
        Usuario u = obtenerPorId(usuarioId);
        u.setEstado(EstadoUsuario.RECHAZADO);
        return usuarioRepository.save(u);
    }

    public Usuario guardarConfig(String usuarioId, com.minomina.model.ConfigNomina nuevaConfig) {
        Usuario u = obtenerPorId(usuarioId);
        u.setConfig(nuevaConfig);
        return usuarioRepository.save(u);
    }
}
