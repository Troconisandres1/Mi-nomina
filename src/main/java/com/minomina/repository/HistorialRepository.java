package com.minomina.repository;

import com.minomina.model.Historial;
import com.minomina.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialRepository extends JpaRepository<Historial, String> {
    List<Historial> findByUsuarioOrderByDesdeDesc(Usuario usuario);
}
