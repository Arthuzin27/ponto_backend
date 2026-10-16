package com.relatar.sistema.ponto.usuario;

import java.time.Instant;

/** @param jornadaDiariaMinutos jornada esperada por dia util (segunda a sexta), em minutos */
public record UsuarioResponse(Long id, String nome, String email, Perfil perfil, boolean ativo,
        int jornadaDiariaMinutos, Instant criadoEm) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(),
                usuario.isAtivo(), usuario.getJornadaDiariaMinutos(), usuario.getCriadoEm());
    }
}
