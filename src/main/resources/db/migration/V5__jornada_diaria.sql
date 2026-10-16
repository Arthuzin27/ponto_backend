-- V5: jornada diaria esperada de cada usuario, base do banco de horas.
-- Regra atual: a jornada vale de segunda a sexta; sabados, domingos (e, por enquanto, feriados) esperam zero.

ALTER TABLE usuario
    ADD COLUMN jornada_diaria_minutos INTEGER NOT NULL DEFAULT 480,
    ADD CONSTRAINT ck_usuario_jornada CHECK (jornada_diaria_minutos BETWEEN 0 AND 720);
