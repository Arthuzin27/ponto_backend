-- V4: endereco aproximado da marcacao e perfil RH renomeado para GESTOR.

-- Endereco (rua, bairro, cidade) obtido pela geocodificacao da localizacao no momento da marcacao.
-- Informativo: pode faltar se o servico de mapas estiver indisponivel.
ALTER TABLE marcacao
    ADD COLUMN endereco VARCHAR(300);

-- O perfil "RH" passa a se chamar "GESTOR" (mesmas permissoes).
ALTER TABLE usuario
    DROP CONSTRAINT ck_usuario_perfil;

UPDATE usuario
SET perfil = 'GESTOR'
WHERE perfil = 'RH';

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN', 'GESTOR', 'FUNCIONARIO'));
