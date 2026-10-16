package com.relatar.sistema.ponto.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.relatar.sistema.ponto.auth.AuthProperties;

/**
 * Cria o primeiro administrador (APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD) quando o banco nao tem nenhum usuario.
 * Depois disso as variaveis sao ignoradas: trocar a senha e feito pela propria aplicacao.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final AuthProperties properties;

    AdminBootstrap(UsuarioRepository usuarios, UsuarioService usuarioService, AuthProperties properties) {
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (!admin.configurado()) {
            log.warn("Nenhum usuario cadastrado e APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD nao definidos: "
                    + "nao sera possivel entrar no sistema");
            return;
        }
        String nome = admin.nome() == null || admin.nome().isBlank() ? "Administrador" : admin.nome();
        usuarioService.criar(nome, admin.email(), admin.senha(), Perfil.ADMIN, Perfil.ADMIN);
        log.info("Administrador inicial criado a partir de APP_ADMIN_EMAIL");
    }
}
