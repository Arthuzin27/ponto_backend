-- V2: usuarios (autenticacao e perfis) e marcacoes de ponto.

CREATE TABLE usuario
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome          VARCHAR(150) NOT NULL,
    -- Sempre gravado em minusculas pela aplicacao.
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    versao        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN', 'RH', 'FUNCIONARIO')),
    CONSTRAINT ck_usuario_email_minusculo CHECK (email = lower(email))
);

CREATE UNIQUE INDEX uk_usuario_email ON usuario (email);

-- Marcacoes sao registros oficiais: somente insercao (ver forbid_mutation em V1).
-- O instante e sempre o do servidor; data_referencia e a data da jornada no fuso da empresa.
CREATE TABLE marcacao
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT      NOT NULL REFERENCES usuario (id),
    tipo            VARCHAR(10) NOT NULL,
    instante        TIMESTAMPTZ NOT NULL,
    data_referencia DATE        NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_marcacao_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA'))
);

CREATE INDEX ix_marcacao_usuario_data ON marcacao (usuario_id, data_referencia, instante);
CREATE INDEX ix_marcacao_usuario_instante ON marcacao (usuario_id, instante DESC);

CREATE TRIGGER trg_marcacao_append_only
    BEFORE UPDATE OR DELETE
    ON marcacao
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();
