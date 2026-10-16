package com.relatar.sistema.ponto.usuario;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.relatar.sistema.ponto.auth.AuthProperties;
import com.relatar.sistema.ponto.auth.AuthenticationFailedException;
import com.relatar.sistema.ponto.common.error.BusinessRuleException;
import com.relatar.sistema.ponto.common.error.ConflictException;
import com.relatar.sistema.ponto.common.error.NotFoundException;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** Sufixo exigido nos e-mails, ex.: "@relatar.com.br". */
    private final String sufixoEmail;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, Clock clock,
            AuthProperties authProperties) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.sufixoEmail = "@" + authProperties.emailDomain().trim().toLowerCase(Locale.ROOT);
    }

    /** Usuario do token, desde que ainda exista e esteja ativo. */
    @Transactional(readOnly = true)
    public Usuario buscarAtivo(Long id) {
        return usuarios.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(AuthenticationFailedException::sessaoInvalida);
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuário não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.findAllByOrderByNomeAsc();
    }

    @Transactional
    public Usuario criar(String nome, String email, String senha, Perfil perfil, Perfil perfilDoAutor) {
        return criar(nome, email, senha, perfil, Usuario.JORNADA_PADRAO_MINUTOS, perfilDoAutor);
    }

    @Transactional
    public Usuario criar(String nome, String email, String senha, Perfil perfil, int jornadaDiariaMinutos,
            Perfil perfilDoAutor) {
        if (perfil == Perfil.ADMIN && perfilDoAutor != Perfil.ADMIN) {
            throw new AccessDeniedException("Somente administradores criam administradores");
        }
        String emailNormalizado = emailCorporativo(email);
        if (usuarios.existsByEmail(emailNormalizado)) {
            throw emailEmUso();
        }
        Usuario usuario = new Usuario(nome.trim(), emailNormalizado, passwordEncoder.encode(senha), perfil,
                clock.instant());
        usuario.definirJornada(jornadaDiariaMinutos);
        return usuarios.save(usuario);
    }

    /**
     * Atualiza os dados cadastrais. Regras: so ADMIN altera administradores ou promove alguem a ADMIN;
     * ninguem altera o proprio perfil (evita perder o ultimo acesso administrativo por engano).
     *
     * @param novaSenha quando informada, redefine a senha do usuario
     */
    @Transactional
    public Usuario atualizar(Long id, String nome, String email, Perfil perfil, int jornadaDiariaMinutos,
            String novaSenha, Long autorId, Perfil perfilDoAutor) {
        Usuario usuario = buscar(id);
        boolean autorEhAdmin = perfilDoAutor == Perfil.ADMIN;
        if ((usuario.getPerfil() == Perfil.ADMIN || perfil == Perfil.ADMIN) && !autorEhAdmin) {
            throw new AccessDeniedException("Somente administradores alteram administradores");
        }
        if (usuario.getId().equals(autorId) && perfil != usuario.getPerfil()) {
            throw new BusinessRuleException("CANNOT_CHANGE_OWN_PROFILE", "Você não pode alterar o próprio perfil.");
        }
        String emailNormalizado = emailCorporativo(email);
        if (!emailNormalizado.equals(usuario.getEmail()) && usuarios.existsByEmail(emailNormalizado)) {
            throw emailEmUso();
        }
        usuario.atualizarDados(nome.trim(), emailNormalizado, perfil, jornadaDiariaMinutos, clock.instant());
        if (novaSenha != null && !novaSenha.isBlank()) {
            usuario.alterarSenha(passwordEncoder.encode(novaSenha), clock.instant());
        }
        return usuario;
    }

    /** E-mail normalizado, desde que seja do dominio corporativo. */
    private String emailCorporativo(String email) {
        String normalizado = normalizarEmail(email);
        String usuarioEmail = normalizado.substring(0, Math.max(0, normalizado.length() - sufixoEmail.length()));
        if (!normalizado.endsWith(sufixoEmail) || usuarioEmail.isEmpty() || usuarioEmail.contains("@")) {
            throw new BusinessRuleException("EMAIL_DOMAIN_NOT_ALLOWED",
                    "Use o e-mail corporativo (" + sufixoEmail + ").");
        }
        return normalizado;
    }

    private static ConflictException emailEmUso() {
        return new ConflictException("EMAIL_IN_USE", "Já existe um usuário com este e-mail.");
    }

    @Transactional
    public Usuario definirAtivo(Long id, boolean ativo, Long autorId, Perfil perfilDoAutor) {
        Usuario usuario = buscar(id);
        if (usuario.getId().equals(autorId)) {
            throw new BusinessRuleException("CANNOT_CHANGE_SELF", "Você não pode alterar o próprio status.");
        }
        if (usuario.getPerfil() == Perfil.ADMIN && perfilDoAutor != Perfil.ADMIN) {
            throw new AccessDeniedException("Somente administradores alteram administradores");
        }
        usuario.definirAtivo(ativo, clock.instant());
        return usuario;
    }

    static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
