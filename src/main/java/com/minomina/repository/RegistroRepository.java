package com.minomina.repository;

import com.minomina.model.Registro;
import com.minomina.model.TipoDia;
import com.minomina.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegistroRepository extends JpaRepository<Registro, String> {

    List<Registro> findByUsuarioOrderByFechaAsc(Usuario usuario);

    Optional<Registro> findByUsuarioAndFecha(Usuario usuario, LocalDate fecha);

    List<Registro> findByUsuarioAndFechaLessThanAndTipoOrderByFechaAsc(Usuario usuario, LocalDate fecha, TipoDia tipo);

    void deleteByUsuarioAndFecha(Usuario usuario, LocalDate fecha);

    void deleteAllByUsuario(Usuario usuario);
}
