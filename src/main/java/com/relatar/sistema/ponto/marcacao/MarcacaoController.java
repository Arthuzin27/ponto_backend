package com.relatar.sistema.ponto.marcacao;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.relatar.sistema.ponto.auth.CurrentUser;

/** Marcacoes do proprio usuario autenticado. */
@RestController
@RequestMapping("/api/marcacoes")
public class MarcacaoController {

    private final MarcacaoService marcacaoService;
    private final LocalPermitidoService localPermitido;

    public MarcacaoController(MarcacaoService marcacaoService, LocalPermitidoService localPermitido) {
        this.marcacaoService = marcacaoService;
        this.localPermitido = localPermitido;
    }

    /**
     * Registra o ponto no horario do servidor. Nenhum dado de horario e aceito do cliente; apenas a
     * localizacao do dispositivo, quando disponivel.
     */
    @PostMapping
    ResponseEntity<DiaResponse.MarcacaoResponse> registrar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) RegistrarMarcacaoRequest request) {
        Localizacao localizacao = request != null ? request.localizacao() : null;
        Marcacao marcacao = marcacaoService.registrar(CurrentUser.id(jwt), localizacao);
        return ResponseEntity.created(URI.create("/api/marcacoes/" + marcacao.getId()))
                .body(DiaResponse.MarcacaoResponse.de(marcacao));
    }

    /**
     * Rua aproximada da localizacao atual e se o ponto pode ser registrado ali. Apenas consulta:
     * a mesma regra e aplicada de novo no registro.
     */
    @PostMapping("/verificar-local")
    LocalPermitidoService.Verificacao verificarLocal(@Valid @RequestBody VerificarLocalRequest request) {
        return localPermitido.verificar(request.localizacao());
    }

    @GetMapping("/hoje")
    MarcacaoService.Hoje hoje(@AuthenticationPrincipal Jwt jwt) {
        return marcacaoService.hoje(CurrentUser.id(jwt));
    }

    @GetMapping
    List<DiaResponse> historico(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return marcacaoService.historico(CurrentUser.id(jwt), de, ate);
    }

    public record RegistrarMarcacaoRequest(@Valid Localizacao localizacao) {
    }

    public record VerificarLocalRequest(@NotNull(message = "Informe a localização.") @Valid Localizacao localizacao) {
    }
}
