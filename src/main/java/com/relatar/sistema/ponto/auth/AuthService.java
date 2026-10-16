package com.relatar.sistema.ponto.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.relatar.sistema.ponto.common.error.BusinessRuleException;
import com.relatar.sistema.ponto.usuario.Usuario;
import com.relatar.sistema.ponto.usuario.UsuarioRepository;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    /** Usado quando o e-mail nao existe, para que o tempo de resposta nao revele isso. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokens,
            Clock clock) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
    }

    @Transactional(readOnly = true)
    public Sessao entrar(String email, String senha) {
        Optional<Usuario> encontrado = usuarios.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        Usuario usuario = encontrado
                .filter(u -> senhaConfere && u.isAtivo())
                .orElseThrow(AuthenticationFailedException::credenciaisInvalidas);
        return new Sessao(tokens.emitir(usuario), usuario);
    }

    @Transactional
    public void alterarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(Usuario::isAtivo)
                .orElseThrow(AuthenticationFailedException::sessaoInvalida);
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "A senha atual não confere.");
        }
        if (senhaAtual.equals(novaSenha)) {
            throw new BusinessRuleException("SAME_PASSWORD", "A nova senha deve ser diferente da atual.");
        }
        usuario.alterarSenha(passwordEncoder.encode(novaSenha), clock.instant());
    }

    public record Sessao(TokenService.TokenEmitido token, Usuario usuario) {
    }
}
