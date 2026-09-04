package com.minomina.repository;

import com.minomina.model.EstadoUsuario;
import com.minomina.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Usuario> findByEstado(EstadoUsuario estado);
}
