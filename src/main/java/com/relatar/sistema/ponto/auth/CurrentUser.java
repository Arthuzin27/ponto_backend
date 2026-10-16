package com.relatar.sistema.ponto.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.relatar.sistema.ponto.usuario.Perfil;

/** Acesso ao usuario autenticado a partir do token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Perfil atual do usuario, conforme carregado do banco em {@link UsuarioAtivoJwtConverter}. */
    public static Perfil perfil(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> Perfil.valueOf(authority.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(AuthenticationFailedException::sessaoInvalida);
    }

    public static Long id(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw AuthenticationFailedException.sessaoInvalida();
        }
    }
}
