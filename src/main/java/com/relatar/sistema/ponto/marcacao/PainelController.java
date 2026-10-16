package com.relatar.sistema.ponto.marcacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.relatar.sistema.ponto.auth.CurrentUser;

@RestController
@RequestMapping("/api")
public class PainelController {

    private final PainelService painel;

    public PainelController(PainelService painel) {
        this.painel = painel;
    }

    /** Equipe hoje: cada usuario ativo com as marcacoes do dia. */
    @GetMapping("/equipe/hoje")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    List<PainelService.MembroHoje> equipeHoje() {
        return painel.equipeHoje();
    }

    /** Banco de horas do proprio usuario. */
    @GetMapping("/banco-horas")
    PainelService.BancoHoras meuBanco(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return painel.bancoDeHoras(CurrentUser.id(jwt), de, ate);
    }

    /** Resumo do banco de horas de cada funcionario. */
    @GetMapping("/banco-horas/equipe")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    List<PainelService.BancoHoras> bancoDaEquipe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return painel.bancoDaEquipe(de, ate);
    }

    /** Banco de horas detalhado de um funcionario. */
    @GetMapping("/banco-horas/usuarios/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    PainelService.BancoHoras bancoDoUsuario(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return painel.bancoDeHoras(id, de, ate);
    }
}
