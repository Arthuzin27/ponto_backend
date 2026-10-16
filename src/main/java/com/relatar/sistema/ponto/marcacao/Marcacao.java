package com.relatar.sistema.ponto.marcacao;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Marcacao de ponto. Somente insercao: o banco rejeita UPDATE e DELETE (trigger forbid_mutation). */
@Entity
@Immutable
@Table(name = "marcacao")
public class Marcacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private TipoMarcacao tipo;

    @Column(nullable = false, updatable = false)
    private Instant instante;

    @Column(name = "data_referencia", nullable = false, updatable = false)
    private LocalDate dataReferencia;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(updatable = false)
    private Double latitude;

    @Column(updatable = false)
    private Double longitude;

    @Column(name = "precisao_metros", updatable = false)
    private Double precisaoMetros;

    @Column(updatable = false, length = 300)
    private String endereco;

    protected Marcacao() {
        // JPA
    }

    public Marcacao(Long usuarioId, TipoMarcacao tipo, Instant instante, LocalDate dataReferencia,
            Localizacao localizacao, String endereco) {
        this.endereco = endereco == null || endereco.length() <= 300 ? endereco : endereco.substring(0, 300);
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.instante = instante;
        this.dataReferencia = dataReferencia;
        this.criadoEm = instante;
        if (localizacao != null) {
            this.latitude = localizacao.latitude();
            this.longitude = localizacao.longitude();
            this.precisaoMetros = localizacao.precisao();
        }
    }

    /** Rua, bairro e cidade aproximados no momento da marcacao, ou null. */
    public String getEndereco() {
        return endereco;
    }

    /** Localizacao do dispositivo, ou null para marcacoes sem localizacao. */
    public Localizacao getLocalizacao() {
        return latitude == null ? null : new Localizacao(latitude, longitude, precisaoMetros);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public TipoMarcacao getTipo() {
        return tipo;
    }

    public Instant getInstante() {
        return instante;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }
}
