-- V1: fundacao do esquema.
-- Regras gerais do esquema (ver docs/DATABASE.md):
--   * instantes sempre em TIMESTAMPTZ (armazenados em UTC);
--   * duracoes sempre em BIGINT de segundos (nunca ponto flutuante);
--   * tabelas de registros oficiais (marcacoes, banco de horas, auditoria) sao append-only,
--     protegidas pela funcao forbid_mutation() abaixo.

-- Necessaria para restricoes EXCLUDE que impedem sobreposicao de vigencias (ex.: jornadas por periodo).
-- E uma extensao "trusted" a partir do PostgreSQL 13: o dono do banco pode cria-la sem superusuario.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Bloqueia UPDATE e DELETE em tabelas append-only. Correcoes devem gerar novos registros
-- vinculados ao original, preservando o historico.
CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Tabela % e somente-insercao: % nao permitido', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

COMMENT ON FUNCTION forbid_mutation() IS
    'Trigger BEFORE UPDATE OR DELETE para tabelas append-only (marcacoes, livro do banco de horas, auditoria).';
