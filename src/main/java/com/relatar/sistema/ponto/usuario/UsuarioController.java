package com.relatar.sistema.ponto.usuario;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.relatar.sistema.ponto.auth.CurrentUser;
import com.relatar.sistema.ponto.marcacao.DiaResponse;
import com.relatar.sistema.ponto.marcacao.MarcacaoService;

/** Gestao de usuarios, restrita a ADMIN e GESTOR. */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final MarcacaoService marcacaoService;

    public UsuarioController(UsuarioService usuarioService, MarcacaoService marcacaoService) {
        this.usuarioService = usuarioService;
        this.marcacaoService = marcacaoService;
    }

    @GetMapping
    List<UsuarioResponse> listar() {
        return usuarioService.listar().stream().map(UsuarioResponse::de).toList();
    }

    @GetMapping("/{id}")
    UsuarioResponse buscar(@PathVariable Long id) {
        return UsuarioResponse.de(usuarioService.buscar(id));
    }

    @PostMapping
    ResponseEntity<UsuarioResponse> criar(Authentication autor, @Valid @RequestBody CriarUsuarioRequest request) {
        int jornada = request.jornadaDiariaMinutos() != null
                ? request.jornadaDiariaMinutos()
                : Usuario.JORNADA_PADRAO_MINUTOS;
        Usuario usuario = usuarioService.criar(request.nome(), request.email(), request.senha(), request.perfil(),
                jornada, CurrentUser.perfil(autor));
        return ResponseEntity.created(URI.create("/api/usuarios/" + usuario.getId()))
                .body(UsuarioResponse.de(usuario));
    }

    /** Altera nome, e-mail e perfil; {@code novaSenha}, quando informada, redefine a senha do usuario. */
    @PutMapping("/{id}")
    UsuarioResponse atualizar(@AuthenticationPrincipal Jwt jwt, Authentication autor, @PathVariable Long id,
            @Valid @RequestBody AtualizarUsuarioRequest request) {
        return UsuarioResponse.de(usuarioService.atualizar(id, request.nome(), request.email(), request.perfil(),
                request.jornadaDiariaMinutos(), request.novaSenha(), CurrentUser.id(jwt), CurrentUser.perfil(autor)));
    }

    @PatchMapping("/{id}/ativo")
    UsuarioResponse definirAtivo(@AuthenticationPrincipal Jwt jwt, Authentication autor, @PathVariable Long id,
            @Valid @RequestBody AtivoRequest request) {
        return UsuarioResponse.de(usuarioService.definirAtivo(id, request.ativo(), CurrentUser.id(jwt),
                CurrentUser.perfil(autor)));
    }

    @GetMapping("/{id}/marcacoes")
    List<DiaResponse> marcacoes(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        usuarioService.buscar(id);
        return marcacaoService.historico(id, de, ate);
    }

    public record CriarUsuarioRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "O nome deve ter até 150 caracteres.")
            String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
            String email,
            @NotBlank(message = "Informe a senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
            @NotNull(message = "Informe o perfil.") Perfil perfil,
            /** Opcional: padrao de 8 horas (480 minutos). */
            @Min(value = 0, message = "Jornada inválida.")
            @Max(value = 720, message = "A jornada diária deve ser de no máximo 12 horas.") Integer jornadaDiariaMinutos) {
    }

    public record AtualizarUsuarioRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "O nome deve ter até 150 caracteres.")
            String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
            String email,
            @NotNull(message = "Informe o perfil.") Perfil perfil,
            @NotNull(message = "Informe a jornada diária.") @Min(value = 0, message = "Jornada inválida.")
            @Max(value = 720, message = "A jornada diária deve ser de no máximo 12 horas.") Integer jornadaDiariaMinutos,
            /** Opcional: vazio mantem a senha atual. */
            @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres.") String novaSenha) {
    }

    public record AtivoRequest(@NotNull(message = "Informe se o usuário está ativo.") Boolean ativo) {
    }
}
