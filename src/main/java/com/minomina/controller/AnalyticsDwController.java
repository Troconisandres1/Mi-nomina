package com.minomina.controller;

import com.minomina.model.Historial;
import com.minomina.model.Registro;
import com.minomina.model.Usuario;
import com.minomina.security.AuthenticatedUser;
import com.minomina.service.DataWarehouseEtlService;
import com.minomina.service.DataWarehouseEtlService.ModeloEstrellaPayload;
import com.minomina.service.HistorialService;
import com.minomina.service.RegistroService;
import com.minomina.service.UsuarioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsDwController {

    private final DataWarehouseEtlService dwEtlService;
    private final UsuarioService usuarioService;
    private final RegistroService registroService;
    private final HistorialService historialService;

    public AnalyticsDwController(DataWarehouseEtlService dwEtlService,
                                 UsuarioService usuarioService,
                                 RegistroService registroService,
                                 HistorialService historialService) {
        this.dwEtlService = dwEtlService;
        this.usuarioService = usuarioService;
        this.registroService = registroService;
        this.historialService = historialService;
    }

    @GetMapping("/dw")
    public ModeloEstrellaPayload obtenerModeloEstrella(@AuthenticationPrincipal AuthenticatedUser auth) {
        if (auth != null) {
            Usuario usuario = usuarioService.obtenerPorId(auth.getUserId());
            List<Registro> registros = registroService.listar(usuario);
            List<Historial> historial = historialService.listar(usuario);
            return dwEtlService.ejecutarEtlUsuario(usuario, registros, historial);
        }
        Usuario demo = new Usuario("operador@minomina.co", "hash", "Operador DW");
        return dwEtlService.ejecutarEtlUsuario(demo, List.of(), List.of());
    }
}
