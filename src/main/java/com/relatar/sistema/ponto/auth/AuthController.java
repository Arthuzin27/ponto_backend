package com.relatar.sistema.ponto.auth;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.relatar.sistema.ponto.usuario.UsuarioResponse;
import com.relatar.sistema.ponto.usuario.UsuarioService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Sessao sessao = authService.entrar(request.email(), request.senha());
        return new LoginResponse(sessao.token().valor(), "Bearer", sessao.token().expiraEm(),
                UsuarioResponse.de(sessao.usuario()));
    }

    @GetMapping("/me")
    UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.de(usuarioService.buscarAtivo(CurrentUser.id(jwt)));
    }

    @PostMapping("/senha")
    ResponseEntity<Void> alterarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AlterarSenhaRequest request) {
        authService.alterarSenha(CurrentUser.id(jwt), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o e-mail.") @Size(max = 254) String email,
            @NotBlank(message = "Informe a senha.") @Size(max = 72) String senha) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UsuarioResponse usuario) {
    }

    public record AlterarSenhaRequest(
            @NotBlank(message = "Informe a senha atual.") @Size(max = 72) String senhaAtual,
            @NotBlank(message = "Informe a nova senha.")
            @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres.") String novaSenha) {
    }
}
