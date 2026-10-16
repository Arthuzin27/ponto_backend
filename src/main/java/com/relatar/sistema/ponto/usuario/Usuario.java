package com.relatar.sistema.ponto.usuario;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "usuario")
public class Usuario {

    public static final int JORNADA_PADRAO_MINUTOS = 480;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo;

    /** Jornada esperada por dia util (segunda a sexta), em minutos. */
    @Column(name = "jornada_diaria_minutos", nullable = false)
    private int jornadaDiariaMinutos = JORNADA_PADRAO_MINUTOS;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    protected Usuario() {
        // JPA
    }

    public Usuario(String nome, String email, String senhaHash, Perfil perfil, Instant agora) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = true;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void atualizarDados(String nome, String email, Perfil perfil, int jornadaDiariaMinutos, Instant agora) {
        this.nome = nome;
        this.email = email;
        this.perfil = perfil;
        this.jornadaDiariaMinutos = jornadaDiariaMinutos;
        this.atualizadoEm = agora;
    }

    public void definirJornada(int jornadaDiariaMinutos) {
        this.jornadaDiariaMinutos = jornadaDiariaMinutos;
    }

    public int getJornadaDiariaMinutos() {
        return jornadaDiariaMinutos;
    }

    public void alterarSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    public void definirAtivo(boolean ativo, Instant agora) {
        this.ativo = ativo;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
