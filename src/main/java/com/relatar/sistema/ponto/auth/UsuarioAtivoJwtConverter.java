package com.relatar.sistema.ponto.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.relatar.sistema.ponto.usuario.Usuario;
import com.relatar.sistema.ponto.usuario.UsuarioRepository;

/**
 * Converte o token em autenticacao consultando o usuario no banco a cada requisicao: usuarios desativados
 * ou removidos perdem o acesso imediatamente (sem esperar o token expirar), e o perfil considerado e sempre
 * o atual, nao o gravado no token.
 */
@Component
public class UsuarioAtivoJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarios;

    UsuarioAtivoJwtConverter(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Usuario usuario = idDoToken(jwt)
                .flatMap(usuarios::findById)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new InvalidBearerTokenException("Usuario inexistente ou inativo"));
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())), jwt.getSubject());
    }

    private static Optional<Long> idDoToken(Jwt jwt) {
        try {
            return Optional.of(Long.valueOf(jwt.getSubject()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
